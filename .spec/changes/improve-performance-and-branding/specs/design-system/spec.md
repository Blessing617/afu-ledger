# Delta：design-system

## MODIFIED Requirements

### Requirement: 背景图换肤
系统 SHALL 允许用户从相册选择一张图片作为主界面背景，并自行调节其「存在感」，同时保证文字始终可读。

可调项 MUST 至少包含：背景可见度、模糊开关、暗色遮罩开关。可见度 MUST 为连续值而不是固定档位。

遮罩只作用于背景层，MUST NOT 降低卡片与文字本身的不透明度。

#### Scenario: 调高背景可见度
- GIVEN 用户已设置自定义背景图，可见度处于默认值
- WHEN 用户把可见度调到最高
- THEN 背景图片清晰、接近原图色彩
- AND 卡片与正文仍保持当前配色可读，不随背景变淡

#### Scenario: 背景与文字对比度不足时自动托底
- GIVEN 用户把可见度调高且关闭暗色遮罩
- WHEN 背景某区域为浅色、正文为深色
- THEN 卡片容器仍保持不透明底色
- AND 正文不在背景图上直接绘制

#### Scenario: 开启模糊
- GIVEN 用户已设置背景图
- WHEN 用户开启「模糊」开关
- THEN 背景图以模糊形式渲染
- AND 调节可见度时模糊程度不跳变

#### Scenario: 恢复默认背景
- GIVEN 用户已设置自定义背景
- WHEN 用户点击「恢复默认」
- THEN 主界面背景回到主题色渐变
- AND 可见度、模糊、遮罩参数一并重置为默认值

#### Scenario: 背景显示参数持久化
- GIVEN 用户调整了可见度与模糊开关
- WHEN 重启应用
- THEN 背景与参数均保持

## ADDED Requirements

### Requirement: 应用图标
系统 SHALL 使用「大肥鱼」形象作为自适应图标（adaptive icon），在 HyperOS 桌面、最近任务与设置的应用列表中都正确显示，且前景主体不超出安全区。

图标 MUST 以矢量或高分辨率位图提供，适配圆形、圆角方形、方形三种遮罩。

#### Scenario: 桌面显示自适应图标
- GIVEN 应用已安装
- WHEN 用户在桌面查看图标
- THEN 显示大肥鱼形象而不是系统默认图标

#### Scenario: 不同遮罩下不被裁切
- GIVEN 系统使用圆形遮罩
- WHEN 渲染图标
- THEN 大肥鱼主体完整可见，耳朵/尾巴不被裁切

#### Scenario: 图片未就绪时的降级
- GIVEN 图标前景资源缺失或解析失败
- WHEN 构建 APK
- THEN 构建失败并给出明确错误，而不是产出无图标包
