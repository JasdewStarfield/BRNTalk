# BRNTalk 双版本发布流程

本流程以 `mc/1.21.1-neoforge` 为发布自动化的主控分支，同时从两个长期维护分支构建并发布同一 BRNTalk 版本：

- `mc/1.21.1-neoforge`：Minecraft 1.21.1、NeoForge、Java 21
- `mc/1.20.1-forge`：Minecraft 1.20.1、Forge、Java 17

最终 GitHub Release 使用版本号作为 tag（例如 `1.2.0`），同时附带两个带 MC 版本的 jar。Modrinth 与 CurseForge 则分别发布两个平台文件，避免把不同加载器/MC 版本错误地标记成同一个兼容组合。

## 一次性配置

1. 本机安装并登录 GitHub CLI：`gh auth login`。
2. 保持两个工作树：
   - `E:\IdeaProjects\BRNTalk`
   - `E:\IdeaProjects\BRNTalk-1.20.1-forge`
3. GitHub 仓库按需配置：

| 类型 | 名称 | 用途 |
| --- | --- | --- |
| Repository variable | `MODRINTH_PROJECT_ID` | Modrinth 项目 ID；仅启用 Modrinth 发布时需要 |
| Repository secret | `MODRINTH_TOKEN` | 具有 `VERSION_CREATE` 权限的 Modrinth PAT |
| Repository variable | `CURSEFORGE_PROJECT_ID` | CurseForge 项目 ID；仅启用 CurseForge 发布时需要 |
| Repository secret | `CURSEFORGE_TOKEN` | CurseForge Author API token |
| Repository secret | `CURSEFORGE_API_KEY` | 可选；只读审计历史文件所需的 Studios/Public API key，不参与上传 |

建议给 GitHub 的 `release` Environment 增加人工审批；若启用，应同时在工作流的发布 job 上配置该 Environment。

## 发布前规则

1. 功能和修复代码必须先分别提交到两个版本分支；发布脚本不会用 `git add -A` 吞入未知改动。
2. 两个工作树的 `CHANGELOG_cn.md` / `CHANGELOG_en.md` 中，`未发布` / `Unreleased` 内容必须同步。
3. 检查 `README.md` 与 `README_en.md`：
   - 安装方式、命令、配置、兼容版本有变化时必须同步修改。
   - 确认无需修改时，用 `-ConfirmReadmeReviewed` 明确记录本次人工判断。
   - 脚本还会检查两个 README 是否包含当前 Minecraft、加载器与 Java 版本，防止 1.20.1/1.21.1 文档串线。
4. 版本号使用 SemVer，并且不得与上一个版本 tag 相同。

## 推荐的一条命令

先把本次说明写在两个工作树的双语 `Unreleased` 区段，再从 NeoForge 工作树执行：

```powershell
.\tools\release\invoke-release.ps1 `
  -Version 1.2.0 `
  -Prepare `
  -ConfirmReadmeReviewed `
  -CommitAndPush `
  -Dispatch
```

该命令依次执行：

1. 确认两个工作树分支正确，且除版本、changelog、README 外没有未提交改动。
2. 将两个 `gradle.properties` 的 `mod_version` 更新为目标版本。
3. 把双语 `Unreleased` 内容滚入带日期的版本区段，并恢复空的 `Unreleased` 占位。
4. 检查双语 README 与目标平台信息。
5. 在两个工作树分别运行：
   - `git diff --check`
   - `.\gradlew.bat build --no-configuration-cache`
6. 每个分支只暂存五个发布元数据文件，分别创建 `Prepare <version> release` commit 并 push。
7. 触发 `.github/workflows/release.yml`；CI 会从远端两个分支重新构建，再创建 GitHub Release 并附上两个 jar。

如果 README 实际有修改，可以省略 `-ConfirmReadmeReviewed`。测试发布可加 `-ReleaseType beta` 或 `alpha`。

## 分阶段执行与恢复

仅整理版本/changelog 并本地验证：

```powershell
.\tools\release\invoke-release.ps1 -Version 1.2.0 -Prepare -ConfirmReadmeReviewed
```

已经手工整理好版本元数据，只运行门禁：

```powershell
.\tools\release\invoke-release.ps1 -Version 1.2.0 -ConfirmReadmeReviewed
```

已经验证，随后提交并推送：

```powershell
.\tools\release\invoke-release.ps1 -Version 1.2.0 -ConfirmReadmeReviewed -CommitAndPush
```

两个分支均已推送，只重新触发 GitHub 发布：

```powershell
.\tools\release\invoke-release.ps1 -Version 1.2.0 -ConfirmReadmeReviewed -Dispatch
```

本地调试脚本时可临时使用 `-SkipBuild`；正式发布不得使用。

## Modrinth 与 CurseForge

平台发布默认关闭。确认 GitHub Variables/Secrets 已配置后显式开启：

```powershell
.\tools\release\invoke-release.ps1 `
  -Version 1.2.0 `
  -ConfirmReadmeReviewed `
  -Dispatch `
  -PublishModrinth `
  -PublishCurseForge
```

- Modrinth 使用 `POST /v2/version` multipart API，沿用既有版本号格式，例如 `mc1.21.1-1.2.0`。脚本在上传前查询已有版本，重复运行会跳过同名版本。
- CurseForge 使用 Author API 的 `/api/projects/{projectId}/upload-file`，通过 `gameVersionNames` 标记 Client、Server、Minecraft 与 Loader。该旧接口没有可靠的幂等键；若 CurseForge 步骤已经成功而工作流后续失败，重跑时应关闭 `publish_curseforge`，或先在作者后台确认是否已有文件。
- token 只从 GitHub Secrets 注入，不写入仓库、命令行示例或构建产物。

### Metadata 模板

平台字段的仓库内来源是 `tools/release/platform-metadata.json`。该模板依据既有 Modrinth 版本固定了：

- 版本名 `BRNTalk <version>` 与版本号 `mc<MC>-<version>`；
- NeoForge 1.21.1 / Forge 1.20.1 两个独立 target；
- `featured=false`、`status=listed`、Client 与 Server 均 required；
- Cloth Config 为 optional dependency；
- CurseForge 对应关系使用 `cloth-config` / project ID `348521`。

本地预检和 CI 都会用 `render-platform-metadata.ps1` 生成两个去敏 JSON：

```text
build/release-metadata/neoforge_1_21_1.json
build/release-metadata/forge_1_20_1.json
```

JSON 中包含最终 changelog、Display Name、版本号、兼容环境、依赖关系和 jar 文件名，可在上传前直接审查；CI 还会把它们保存为 `release-metadata` artifact。

`platform-metadata-audit.yml` 是只读审计工作流。`CURSEFORGE_TOKEN` 是作者上传 Token，不能读取 Studios/Public API 的历史文件；如需在审计报告中包含 CurseForge 既有文件和 changelog，额外配置 `CURSEFORGE_API_KEY`。

## GitHub Release 产物

成功后 Release 应至少包含：

```text
brntalk-mc1.21.1-<version>.jar
brntalk-mc1.20.1-<version>.jar
```

发布说明由 `CHANGELOG_cn.md` 与 `CHANGELOG_en.md` 的目标版本区段自动合并。若 GitHub Release 已存在，工作流会复用它并补传缺失附件，因此 GitHub 步骤可安全重跑。

## 失败处理

- 本地 build 失败：不 commit、不 push，先在对应工作树修复后重跑。
- 第一个分支 push 成功、第二个失败：修复第二个分支后使用 `-CommitAndPush` 重跑；已无新改动的分支不会创建空 commit。
- CI build 失败：修复并 push 对应分支，再用 `-Dispatch` 重跑。
- GitHub Release 成功、Modrinth/CurseForge 失败：在 Actions 中只重跑需要的平台，或重新 dispatch 时只打开尚未成功的平台开关。
- 不通过移动 tag 或覆盖已发布 jar 来“修复”正式版本；应递增补丁版本重新发布。
