# PaC DSA 双ROI插件：GitHub发布候选版

此版本为`2026.10.25-rc.2`发布候选版，正式标签和在线测试状态请以仓库为准。插件是 **Java** 编写的ImageJ1/Fiji插件，不是Python插件或ImageJ Macro。Python脚本仅用于开发者构建和测试。

## 版本与来源

- 打包版本：`2026.10.25-rc.2`。
- 算法版本及软件界面版本：`2026.10.07-quality.1`。
- 原有测量源码保持不变；此次补齐菜单配置、安装说明、合成示例和测试。
- 原源码没有可用的历史Git提交记录，使用`release.json`中的SHA-256定位源码快照。正式发布时再记录真实Git提交和标签，不能用哈希冒充旧提交。
- 许可证：MIT。版权声明为 `Copyright (c) 2026 yujiechen`，按用户2026-10-25确认的个人归属填写。
- 公开仓库：[Chenyujiemed123a/PaC-DSA-2ROI](https://github.com/Chenyujiemed123a/PaC-DSA-2ROI)，创建于2026-10-25。

## 许可和引用

完整许可见`LICENSE`，JAR内也包含同一许可。第三方依赖保留其各自条款，MIT声明不能覆盖无权重新许可的代码。研究软件及未完成临床验证的说明属于适用性提醒，不是在MIT之外追加“禁止商用”等限制。

`CITATION.bib`提供建议引用，作者按确认的`yujiechen`原样保存，未擅自拆分姓名或添加机构，已填写真实仓库地址。引用时还应记录实际使用的Git提交或版本标签；软件DOI尚未分配。推荐引用不等于MIT强制要求引用论文。

## 安装与示例

1. 把旧版`PaC_DSA_2ROI.jar`备份到Fiji目录之外，避免两个同名插件同时存在。
2. 将`dist/PaC_DSA_2ROI.jar`复制到Fiji的`plugins`目录，重启Fiji。
3. 在菜单 **Plugins → PaC DSA 2ROI** 启动。
4. 测试时打开`examples/synthetic/synthetic_6fps.tif`，在空的ROI Manager中通过 **More → Open** 加载`synthetic_ROIs.zip`。
5. 参数：自动检测开启；平滑半径1；连续上升跨度3；噪声倍数3；主峰阈值90%；最大首过时长3秒；帧率6帧/秒。
6. 预期整数TTP为17、19，差值为 **+2帧＝0.3333秒**。示例是人工生成的条带和高斯曲线，不是真实患者影像。

## 日常使用要点

- ROI Manager中的先后顺序决定角色：第一个近端/参考，第二个远端/比较；差值始终为ROI2−ROI1。
- 仅有两个ROI时自动使用两者；有更多ROI时必须恰好选中本次两个，角色仍按管理器列表顺序，而非点击顺序。
- 核对真实采集帧率。不能因研究通常为6帧/秒就替所有影像填写6。
- 插件会尝试识别信号正负极性，不要求所有数据统一做像素Invert；不要反复Invert作为修正不合理结果的方法。
- 基线校正是每条曲线减去一个基线数值，不等同于逐帧背景ROI扣除。
- `Local parabolic fit accepted`只表示局部三点拟合通过检查，不代表ROI稳定或测量准确。`Peak_selection_stability = Not tested`不是错误，而是明确说明本插件未自动开展空间重复性验证。
- 保存原始影像引用、ROI ZIP、位置图、曲线、结果和参数；PNG/TIFF位置图不能替代可复用的ROI ZIP。
- 独立重新放置ROI并复算属于外部质控。既定“差异≤1帧”可作为项目规则记录，但不是本软件已证实的临床准确性界限。异常或差异过大按项目规则双人核实，不挑选最接近期望的一次覆盖其它结果。

插件本身输出TTP及其差值，**不直接输出血流量**，也不自动确认血管解剖或病变是否适合测量。

## 测试与文件

已通过本机10组合成测试，包括ROI选择和边界、正负极性、已知2帧延迟、首过峰、局部拟合拒绝、质控语义，以及保存后的TIFF+ROI ZIP重读。详情见`validation/build-and-test.json`。

支持构建：JDK 21、Python 3.9+；插件运行只依赖Fiji/ImageJ与Java。参考依赖ImageJ 1.54p。Windows/Linux流程已配置但尚未在GitHub执行，不能写成已通过。

尚未执行患者精度验证，不能把合成测试通过等同于真实病例准确。本包不包含任何真实患者资料。

[英文README](README.md) · [安装与保存](docs/INSTALL.md) · [算法与限制](docs/METHODS.md) · [发布前清单](docs/PUBLISH_CHECKLIST.md)
