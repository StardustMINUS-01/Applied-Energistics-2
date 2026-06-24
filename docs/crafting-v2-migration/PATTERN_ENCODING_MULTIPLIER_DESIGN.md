# 处理样板倍率按钮方案

## 目标

复刻 GTL 样板编码终端中的处理样板倍率按钮，在当前 AE2 1.21.1 源码结构中直接实现，不使用 mixin，不替换整套样式文件。

## 功能范围

只在 **ME样板编码终端** 的 **处理样板** 模式中增加 6 个按钮：

```text
x2  x3  x5
÷2  ÷3  ÷5
```

按钮会同时缩放处理样板的输入配置库存和输出配置库存：

- `encodedInputsInv`
- `encodedOutputsInv`

工作台样板、石切样板、锻造样板不参与该功能。

## 服务端语义

`PatternEncodingTermMenu` 新增客户端动作：

```text
scaleProcessingPattern(Integer factor)
```

客户端按钮点击后发送倍率到服务端。服务端只接受：

```text
2, 3, 5, -2, -3, -5
```

正数表示乘法，负数表示除法。

缩放采用 all-or-nothing 语义：

1. 当前模式必须是 `EncodingMode.PROCESSING`。
2. 空槽跳过。
3. 乘法时，任意非空槽结果超过 `Integer.MAX_VALUE`，整次操作失败。
4. 除法时，任意非空槽数量不能整除倍率，整次操作失败。
5. 除法结果小于 1，整次操作失败。
6. 输入和输出全部验证通过后，才统一写回槽位。

失败时不修改任何输入或输出槽。

## 客户端布局

在 `ProcessingEncodingPanel` 中增加 6 个短文本按钮，挂到现有 `processing.json`：

```text
processingScaleX2
processingScaleX3
processingScaleX5
processingScaleDiv2
processingScaleDiv3
processingScaleDiv5
```

按钮只在处理样板面板可见。按钮文案使用短文本，tooltip 使用本地化文本说明会同时修改输入和输出数量。

## 推荐修改文件

- `src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java`
- `src/main/java/appeng/client/gui/me/items/ProcessingEncodingPanel.java`
- `src/main/java/appeng/client/gui/widgets/ProcessingPatternScaleButton.java`
- `src/main/resources/assets/ae2/screens/terminals/encoding/processing.json`
- `src/main/java/appeng/core/localization/ButtonToolTips.java`
- `src/main/resources/assets/ae2/lang/zh_cn.json`
- `src/test/java/appeng/menu/me/items/ProcessingPatternScalerTest.java`

## 验证计划

先补服务端缩放逻辑测试，再实现：

1. 输入和输出同时乘以 2。
2. 空槽保持空。
3. 输入和输出同时除以 2。
4. 除法不能整除时不修改任何槽。
5. 乘法超过 `Integer.MAX_VALUE` 时不修改任何槽。
6. 非法倍率不修改。

验证命令：

```powershell
<本地 Gradle 8.12.1> --no-daemon test --tests appeng.menu.me.items.ProcessingPatternScalerTest
<本地 Gradle 8.12.1> --no-daemon test
```
