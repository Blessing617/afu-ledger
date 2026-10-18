"""重建 `app/src/main/assets/ocr/` 里的 OCR 模型资产。

为什么需要这个脚本：仓库里的模型不是原始 onnx，而是经过两步处理的 `.ort`：

1. **把 BatchNormalization 展开成 Mul + Add** —— `onnxruntime-mobile` 裁掉了 BN 算子，
   但推理期 BN 只是仿射变换，可以预先算成常量。
2. **转换成 ORT 格式** —— `onnxruntime-mobile` 不读 `.onnx`，且转换时必须用
   `--optimization_style Runtime`，否则会融合出 mobile 包没有的 QuickGelu 算子。

依赖：

    pip install onnx onnxoptimizer onnxruntime

用法：

    python tools/ocr/prepare_ocr_models.py

脚本会把结果写到 `app/src/main/assets/ocr/`，并把临时产物放在 `build/ocr-work/`。
"""

import argparse
import pathlib
import shutil
import subprocess
import sys
import urllib.request

import numpy as np
import onnx
import onnxoptimizer
from onnx import helper, numpy_helper

# RapidOCR 在 ModelScope 上的镜像，国内直连可达（GitHub / HuggingFace 常被重置）
MODELSCOPE = "https://www.modelscope.cn/api/v1/models/RapidAI/RapidOCR/repo?Revision=master&FilePath="

SOURCES = {
    "det.onnx": "onnx/PP-OCRv4/det/ch_PP-OCRv4_det_mobile.onnx",
    "rec.onnx": "onnx/PP-OCRv4/rec/ch_PP-OCRv4_rec_mobile.onnx",
    "ppocr_keys_v1.txt": "paddle/PP-OCRv4/rec/ch_PP-OCRv4_rec_mobile/ppocr_keys_v1.txt",
}

EXPECTED_BYTES = {
    "det.onnx": 4_745_517,
    "rec.onnx": 10_857_958,
    "ppocr_keys_v1.txt": 26_249,
}


def download(work_dir: pathlib.Path) -> None:
    for name, remote in SOURCES.items():
        target = work_dir / name
        if target.is_file() and target.stat().st_size == EXPECTED_BYTES[name]:
            print(f"skip {name}（已存在）")
            continue
        print(f"download {name} …")
        with urllib.request.urlopen(MODELSCOPE + remote, timeout=180) as response:
            target.write_bytes(response.read())
        size = target.stat().st_size
        if size != EXPECTED_BYTES[name]:
            raise SystemExit(f"{name} 大小不符：{size} != {EXPECTED_BYTES[name]}")


def fuse_bn(work_dir: pathlib.Path) -> pathlib.Path:
    """用 onnxoptimizer 把能折叠的 BN 折进卷积，剩下的交给 expand_bn。"""
    source = work_dir / "rec.onnx"
    target = work_dir / "rec_fused.onnx"
    model = onnx.load(str(source))
    optimized = onnxoptimizer.optimize(
        model,
        [
            "fuse_bn_into_conv",
            "eliminate_nop_transpose",
            "fuse_consecutive_transposes",
            "eliminate_deadend",
        ],
    )
    onnx.save(optimized, str(target))
    return target


def expand_bn(source: pathlib.Path, target: pathlib.Path) -> None:
    """把剩下的 BatchNormalization 展开成 Mul + Add（参数是常量，可预计算）。"""
    model = onnx.load(str(source))
    graph = model.graph

    initializers = {init.name: init for init in graph.initializer}

    # BN 的 scale/bias/mean/var 常常是 Constant 节点的输出而不是 initializer
    constants = {}
    for node in graph.node:
        if node.op_type != "Constant":
            continue
        for attr in node.attribute:
            if attr.name == "value":
                constants[node.output[0]] = numpy_helper.to_array(attr.t)

    def param(name: str) -> np.ndarray:
        if name in initializers:
            return numpy_helper.to_array(initializers[name])
        if name in constants:
            return constants[name]
        raise KeyError(f"找不到 BN 参数：{name}")

    new_inits = []
    new_nodes = []
    replaced = 0
    for node in graph.node:
        if node.op_type != "BatchNormalization":
            new_nodes.append(node)
            continue

        scale = param(node.input[1])
        bias = param(node.input[2])
        mean = param(node.input[3])
        var = param(node.input[4])

        eps = 1e-5
        for attr in node.attribute:
            if attr.name == "epsilon":
                eps = attr.f

        gamma = scale / np.sqrt(var + eps)
        beta = bias - mean * gamma
        # ONNX 广播按尾部对齐，必须 reshape 成 [1, C, 1, 1] 才能按通道广播
        gamma = gamma.reshape(1, -1, 1, 1).astype(np.float32)
        beta = beta.reshape(1, -1, 1, 1).astype(np.float32)

        gamma_name = node.name + "_gamma"
        beta_name = node.name + "_beta"
        new_inits.append(numpy_helper.from_array(gamma, gamma_name))
        new_inits.append(numpy_helper.from_array(beta, beta_name))
        new_nodes.append(
            helper.make_node("Mul", [node.input[0], gamma_name], [node.name + "_mul"], name=node.name + "_mul")
        )
        new_nodes.append(
            helper.make_node("Add", [node.name + "_mul", beta_name], [node.output[0]], name=node.name + "_add")
        )
        replaced += 1

    del graph.node[:]
    graph.node.extend(new_nodes)
    graph.initializer.extend(new_inits)
    onnx.checker.check_model(model)
    onnx.save(model, str(target))
    print(f"  {source.name}: 展开 {replaced} 个 BN 节点")


def to_ort(work_dir: pathlib.Path, ort_dir: pathlib.Path) -> None:
    cmd = [
        sys.executable,
        "-m",
        "onnxruntime.tools.convert_onnx_models_to_ort",
        str(work_dir / "flat"),
        "--output_dir",
        str(ort_dir),
        "--target_platform",
        "arm",
        "--optimization_style",
        "Runtime",
    ]
    print("convert:", " ".join(cmd))
    subprocess.run(cmd, check=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--assets-dir", default="app/src/main/assets/ocr")
    parser.add_argument("--work-dir", default="build/ocr-work")
    args = parser.parse_args()

    work_dir = pathlib.Path(args.work_dir)
    assets_dir = pathlib.Path(args.assets_dir)
    work_dir.mkdir(parents=True, exist_ok=True)

    download(work_dir)

    flat_dir = work_dir / "flat"
    if flat_dir.exists():
        shutil.rmtree(flat_dir)
    flat_dir.mkdir()

    print("处理 det …")
    expand_bn(work_dir / "det.onnx", flat_dir / "det.onnx")

    print("处理 rec …")
    expand_bn(fuse_bn(work_dir), flat_dir / "rec.onnx")

    ort_dir = work_dir / "ort"
    if ort_dir.exists():
        shutil.rmtree(ort_dir)
    to_ort(work_dir, ort_dir)

    # Runtime 风格只会产出 *.with_runtime_opt.ort，它里面同时含基础图与优化记录
    assets_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy(ort_dir / "det.with_runtime_opt.ort", assets_dir / "det.ort")
    shutil.copy(ort_dir / "rec.with_runtime_opt.ort", assets_dir / "rec.ort")
    shutil.copy(work_dir / "ppocr_keys_v1.txt", assets_dir / "ppocr_keys_v1.txt")

    for name in ("det.ort", "rec.ort", "ppocr_keys_v1.txt"):
        size = (assets_dir / name).stat().st_size
        print(f"{assets_dir / name}  {size:,} bytes")

    print("\n完成。若模型内容变了，记得同时递增 OcrModelInstaller.MODEL_VERSION。")


if __name__ == "__main__":
    main()
