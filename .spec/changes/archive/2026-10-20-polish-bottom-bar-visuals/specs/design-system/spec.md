# Delta：design-system

## MODIFIED Requirements

### Requirement: 圆润控件风格
系统 SHALL 在全局使用大圆角与胶囊形控件，形成统一的柔和视觉。

#### Scenario: 主操作按钮为胶囊形
- GIVEN 任意屏幕上的主操作按钮
- WHEN 按钮被渲染
- THEN 其形状为胶囊形（圆角半径等于高度的一半）
- AND 点击水波纹被裁剪在胶囊形状内

#### Scenario: 卡片与输入框圆角统一
- GIVEN 首页卡片与记账页输入框
- WHEN 两者被渲染
- THEN 卡片圆角为 20dp、输入框圆角为 16dp
- AND 两者圆角来自同一份 Shape 定义，修改后全应用同步生效

#### Scenario: 底栏等自定义形状左右对称
- GIVEN 底部导航使用自定义形状（中央凹槽）
- WHEN 该形状被绘制
- THEN 左右两端的圆角参数一致、视觉对称
- AND 相邻线段之间是相切过渡，MUST NOT 出现斜切或直角断口

#### Scenario: 点击反馈跟随控件形状
- GIVEN 底部导航的任意标签
- WHEN 用户按下标签
- THEN 水波纹被裁剪为圆角矩形
- AND MUST NOT 出现矩形直角的涟漪
