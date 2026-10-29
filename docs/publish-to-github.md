# 上传到 GitHub

本地仓库已经初始化并提交好了（分支 `main`，1 个提交），只剩“建远程仓库 + 推送”两步。

## 1. 建远程仓库

打开 <https://github.com/new>：

- **Repository name**：`Time-Manager-Android`
- **Description**：可填 `时间管理器 · Android 版（Kotlin + Jetpack Compose）`
- **Private / Public**：按喜好
- **不要**勾选 “Add a README file”、“Add .gitignore”、“Choose a license”——本地都已经有了，勾了会产生冲突

点 **Create repository**。

## 2. 推送

把 `<你的用户名>` 换成你的 GitHub 用户名，然后在项目目录执行：

```powershell
cd C:\Users\35076\Desktop\Time-Manager-Android
git remote add origin https://github.com/<你的用户名>/Time-Manager-Android.git
git push -u origin main
```

第一次推送会弹出浏览器窗口让你登录 GitHub（Git Credential Manager 已经装好了），登录并授权即可，之后不用再输密码。

推送完成后仓库地址就是 `https://github.com/<你的用户名>/Time-Manager-Android`。

## 3. 确认自动构建

仓库里带了 `.github/workflows/build.yml`：每次 push 都会在 GitHub 的服务器上跑一遍单元测试并打出 debug APK。

推上去之后打开仓库的 **Actions** 标签页，等 “Build APK” 变绿，点进去在页面底部的 **Artifacts** 里就能下载 `Time-Manager-Android-debug`（就是 APK 的 zip 包）。

## 4. 发布可下载的 APK（可选）

在仓库右侧 **Releases → Draft a new release**：

- **Choose a tag**：输入 `v1.0.1`，点 “Create new tag”
- **Title**：`1.0.1`
- **Describe**：贴一段更新说明（可直接用 README 里的“更新记录”）
- 把 `dist\Time-Manager-Android-1.0.1-debug.apk` 拖到附件区

这样别人打开仓库首页就能直接下载安装包。

> 这个 APK 是 **debug 签名**的，适合自己用和内部测试。要上架应用商店或长期发布，需要自己生成 keystore 签 release 包，并且**务必备份好 keystore 和密码**——一旦丢了，同一个包名就再也无法覆盖升级。

## 以后怎么更新

```powershell
cd C:\Users\35076\Desktop\Time-Manager-Android
git status                  # 看改了什么
git add -A
git commit -m "修复：xxx"
git push
```

## 常见问题

- **push 报 403 / 认证失败**：`git credential-manager` 缓存的账号不对。执行 `git credential-manager logout` 后重试，或改用 Personal Access Token 作为密码。
- **push 报 “repository not found”**：多半是用户名写错，或仓库名大小写不一致。用 `git remote -v` 检查，`git remote set-url origin <新地址>` 修改。
- **不想用命令行**：装 [GitHub Desktop](https://desktop.github.com/)，选 “Add existing repository”，指向 `C:\Users\35076\Desktop\Time-Manager-Android`，然后点 Publish repository。
- **提交署名不对**：本仓库的 `user.name` / `user.email` 已按 `枫殇林叶 / 3507609128@qq.com` 设为仓库级配置；想全局生效执行
  ```powershell
  git config --global user.name "枫殇林叶"
  git config --global user.email "3507609128@qq.com"
  ```
