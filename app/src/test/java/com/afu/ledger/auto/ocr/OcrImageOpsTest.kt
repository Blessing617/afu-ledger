package com.afu.ledger.auto.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 预处理回归测试。
 *
 * 缩放已经改为交给系统（[BitmapScaler]），这里只能验证**纯逻辑部分**：
 * 尺寸计算、归一化数值、裁切、以及「尺寸不符时必须拒绝」这条安全约束。
 */
class OcrImageOpsTest {

    @Test
    fun `det 目标尺寸是 32 的倍数且不超过长边上限`() {
        val sizes = OcrImageOps.detTargetSize(width = 1440, height = 3200, limitSide = 960)
        assertEquals(0, sizes[0] % 32)
        assertEquals(0, sizes[1] % 32)
        assertTrue("长边不应超过上限，实际 ${sizes[1]}", sizes[1] <= 960 + 32)
    }

    @Test
    fun `小于上限的图不放大`() {
        val sizes = OcrImageOps.detTargetSize(width = 300, height = 200, limitSide = 960)
        assertTrue(sizes[0] <= 320)
        assertTrue(sizes[1] <= 224)
    }

    @Test
    fun `det 输入尺寸不符时返回 null 而不是瞎算`() {
        val image = OcrImage(width = 4, height = 4, argb = IntArray(16))
        assertNull(OcrImageOps.detInput(image, targetWidth = 8, targetHeight = 8))
    }

    @Test
    fun `det 归一化数值符合 ImageNet 参数`() {
        // 纯白像素：R=G=B=255 → (1.0 - mean) / std
        val white = OcrImage(width = 1, height = 1, argb = intArrayOf(0xFFFFFFFF.toInt()))
        val input = OcrImageOps.detInput(white, 1, 1)
        assertNotNull(input)
        val flat = input!!
        assertEquals(3, flat.size)
        assertClose((1f - 0.485f) / 0.229f, flat[0])
        assertClose((1f - 0.456f) / 0.224f, flat[1])
        assertClose((1f - 0.406f) / 0.225f, flat[2])
    }

    @Test
    fun `det 归一化为 CHW 排列`() {
        // 2x2：第一个像素纯红，其余黑
        val image = OcrImage(
            width = 2,
            height = 2,
            argb = intArrayOf(
                0xFFFF0000.toInt(), 0xFF000000.toInt(),
                0xFF000000.toInt(), 0xFF000000.toInt(),
            ),
        )
        val flat = OcrImageOps.detInput(image, 2, 2)!!
        assertEquals(12, flat.size)
        // R 平面第一个元素最大（纯红 R=1.0），同平面其余像素是黑色 R=0
        assertTrue("R 平面首元素应最高，实际 ${flat[0]}", flat[0] > flat[1])
        // G/B 平面的首元素是纯红的 G=B=0，与黑色像素同为 0，数值相等
        assertClose(flat[4], flat[5])
        assertClose(flat[8], flat[9])
        // 且 R 平面首元素明显大于 G/B 平面首元素
        assertTrue("纯红像素的 R 分量应高于 G 分量", flat[0] > flat[4])
    }

    @Test
    fun `rec 目标尺寸高度固定 48`() {
        val patch = OcrImage(width = 200, height = 50, argb = IntArray(200 * 50))
        val size = OcrImageOps.recTargetSize(patch)!!
        assertEquals(OcrImageOps.REC_HEIGHT, size[1])
        assertTrue(size[0] <= OcrImageOps.REC_MAX_WIDTH)
    }

    @Test
    fun `rec 超宽行被截到上限`() {
        val patch = OcrImage(width = 5000, height = 48, argb = IntArray(5000 * 48))
        val size = OcrImageOps.recTargetSize(patch)!!
        assertEquals(OcrImageOps.REC_MAX_WIDTH, size[0])
    }

    @Test
    fun `rec 归一化到 -1 到 1`() {
        val patch = OcrImage(width = 1, height = 1, argb = intArrayOf(0xFFFFFFFF.toInt()))
        val flat = OcrImageOps.recTensor(patch, 1, 1)!!
        assertClose(1f, flat[0])
        assertClose(1f, flat[1])
        assertClose(1f, flat[2])
    }

    @Test
    fun `rec 输入尺寸不符时返回 null`() {
        val patch = OcrImage(width = 4, height = 4, argb = IntArray(16))
        assertNull(OcrImageOps.recTensor(patch, targetWidth = 8, targetHeight = 8))
    }

    @Test
    fun `裁切越界自动收紧`() {
        val image = OcrImage(width = 10, height = 10, argb = IntArray(100))
        val cropped = OcrImageOps.crop(image, left = -5, top = -5, right = 100, bottom = 100)!!
        assertEquals(10, cropped.width)
        assertEquals(10, cropped.height)
    }

    @Test
    fun `裁切尺寸正确且内容取自对应区域`() {
        // 10x10，值为 y*10+x
        val argb = IntArray(100) { it }
        val image = OcrImage(width = 10, height = 10, argb = argb)
        val cropped = OcrImageOps.crop(image, left = 2, top = 3, right = 5, bottom = 7)!!
        assertEquals(3, cropped.width)
        assertEquals(4, cropped.height)
        // (0,0) 应取原图 (2,3) = 32
        assertEquals(32, cropped.argb[0])
    }

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 1e-4f) {
        assertTrue(
            "期望 $expected，实际 $actual",
            abs(expected - actual) <= tolerance,
        )
    }
}
