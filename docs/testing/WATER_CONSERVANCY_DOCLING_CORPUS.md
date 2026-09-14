# 水利工程知识库与 Docling 测试语料

核验日期：2026-09-07。

本文列出适合验证 PDF、DOCX 和旧版 DOC 解析链路的真实公开资料。标准状态可能变化，业务问答引用前应再次访问全国标准信息公共服务平台核验。

## 论文 PDF

1. 刘宪东、谷艳昌、王士军、陆强、王宏：《均质土坝管涌溃决实验渗流分析》，《中国农村水利水电》2024 年第 6 期，166–173 页，DOI：10.12396/znsd.231643。期刊页提供 PDF（约 2.1 MB）：<https://irrigate.whu.edu.cn/CN/10.12396/znsd.231643>
2. 卢渊博、贾万波、王鑫科：《运行期土石坝渗流量与库水位变化过程的关系研究》，《水利建设与管理》2025 年第 4 期。可直接下载的 7 页 PDF：<https://www.sljsygl.com/UpFiles/2025-4-28/638814359484070853.pdf>
3. 刘松林、金宇、张生阳、程方军：Dam Seepage Analysis Based on Causal Testing and Regression Analysis，Water 2026, 18(11), 1359，DOI：10.3390/w18111359。开放获取页面可下载 PDF：<https://doi.org/10.3390/w18111359>
4. Ahmed M. S. Al-Janabi 等：Comparison Analysis of Seepage Through Homogenous Embankment Dams Using Physical, Mathematical and Numerical Models，Arabian Journal for Science and Engineering 50, 8143–8152 (2025)，DOI：10.1007/s13369-024-09224-x。开放获取页面可下载 PDF：<https://doi.org/10.1007/s13369-024-09224-x>
5. Ali Torabi Haghighi、Anne Tuomela、Ali Akbar Hekmatzadeh：Assessing the Efficiency of Seepage Control Measures in Earthfill Dams，Geotechnical and Geological Engineering 38, 5667–5680 (2020)，DOI：10.1007/s10706-020-01371-w。开放获取页面可下载 PDF：<https://doi.org/10.1007/s10706-020-01371-w>

建议重点检查中文字体编码、双栏阅读顺序、公式、图题、跨页表格、页码和标题路径。第 2 篇部分中文字体的文本层编码较复杂，适合验证 OCR 与原生文本抽取之间的差异。

## 中国水利工程规范官方页面

1. SL 252—2017《水利水电工程等级划分及洪水标准》：现行、强制性；2017-04-09 实施，代替 SL 252—2000。<https://std.samr.gov.cn/hb/search/stdHBDetailed?id=8B1827F24785BB19E05397BE0A0AB44A>
2. SL/T 223—2025《水利水电建设工程验收规程》：现行、推荐性；2025-06-14 实施，代替 SL 223—2008 和 SL 176—2007。<https://std.samr.gov.cn/hb/search/stdHBDetailed?id=513692AE0F9BBA74E06397BE0A0AF4F6>
3. SL/T 191—2025《水工混凝土结构设计规范》：现行、推荐性；2026-02-05 实施，代替 SL 191—2008。<https://std.samr.gov.cn/hb/search/stdHBDetailed?id=515EC31589B6586CE06397BE0A0AEAC8>
4. SL 677—2014《水工混凝土施工规范》：现行、强制性；2015-01-27 实施。<https://std.samr.gov.cn/hb/search/stdHBDetailed?id=8B1827F24C55BB19E05397BE0A0AB44A>
5. SL/T 744—2016《水工建筑物荷载设计规范》：现行、推荐性；2017-02-25 实施。<https://std.samr.gov.cn/hb/search/stdHBDetailed?id=8B1827F24D71BB19E05397BE0A0AB44A>
6. SL/T 386—2025《水利水电工程边坡与挡土墙设计规范》：现行、推荐性；2026-02-05 实施，代替 SL 386—2007 和 SL 379—2007。<https://std.samr.gov.cn/hb/search/stdHBDetailed?id=515EC31589B7586CE06397BE0A0AEAC8>

这些链接是标准信息官方核验入口。页面显示“查看文本”时可用于结构化文本测试；若只有元数据，不应把网页摘要当作标准全文。

## Word 测试说明

- `.docx`：直接上传真实的项目报告、审批示范文本或将有权使用的规范原文保存为 DOCX；预期解析器元数据为 `parser=docling`。
- `.doc`：上传 Word 97–2003 二进制文档；预期走 Tika/POI HWPF，解析器元数据为 `parser=tika-poi-hwpf`。
- 不要仅修改文件扩展名来制造 DOC 测试文件。DOC 与 DOCX 的内部格式不同，应使用 Word 或 LibreOffice 的“另存为 Word 97–2003”功能生成真实 `.doc`。

## 建议验证问题

1. SL/T 223—2025 替代了哪些旧标准，何时实施？
2. SL 252—2017 的标准名称、主管部门和实施日期是什么？
3. 论文对坝体渗流量与库水位关系给出了什么结论，数据和方法有哪些限制？
4. 对同一问题同时召回论文与规范时，智能体是否区分“研究结论”和“强制/推荐性技术要求”？
5. 上传旧版 DOC 后，审核页是否明确显示 Tika/POI 而不是 Docling？
