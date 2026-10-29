# ============================================================================
#  时间管理器 Android — 一键发布脚本
#  用法：在项目根目录执行
#      .\release.ps1 -Version 1.0.4 -Message "1.0.4：修复 XXX"
#  做的事：升版本号 -> 跑单元测试 -> 打包 APK -> 拷贝到 dist\ -> git 提交并推送
#  说明：versionCode 自动 = 原来的 versionCode + 1，不要手动改。
# ============================================================================
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][string]$Message,
    # 本机 git 全局配置了 http://127.0.0.1:12000 代理，但代理软件没开时推送会失败；
    # 这个开关让本次推送绕过代理直连 github（默认开启，失败时再手动处理）。
    [switch]$BypassProxy = $true,
    # 只在本机打包、不推送（代码和 APK 照样产出）
    [switch]$NoPush
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
Set-Location $root

$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
$py = "C:\Users\35076\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe"
$gradleFile = Join-Path $root "app\build.gradle.kts"

# ---------- 1. 升版本号 ----------
$text = Get-Content $gradleFile -Raw
if ($text -notmatch 'versionCode\s*=\s*(\d+)') { throw "没找到 versionCode" }
$code = [int]$Matches[1] + 1
$text = [regex]::Replace($text, 'versionCode\s*=\s*\d+', "versionCode = $code")
$text = [regex]::Replace($text, 'versionName\s*=\s*"[^"]*"', "versionName = `"$Version`"")
Set-Content $gradleFile $text -NoNewline -Encoding utf8
Write-Host "==> 版本号：versionName = $Version, versionCode = $code" -ForegroundColor Cyan

# ---------- 2. 单元测试 ----------
Write-Host "==> 运行单元测试..." -ForegroundColor Cyan
& .\gradlew.bat --console=plain testDebugUnitTest | Select-String -Pattern "^e:|FAILED|BUILD"
if ($LASTEXITCODE -ne 0) { throw "单元测试失败，已中止发布" }

# ---------- 3. 打包 APK ----------
Write-Host "==> 打包 APK..." -ForegroundColor Cyan
& .\gradlew.bat --console=plain assembleDebug | Select-String -Pattern "^e:|FAILED|BUILD"
if ($LASTEXITCODE -ne 0) { throw "打包失败，已中止发布" }

# ---------- 4. 拷贝到 dist ----------
$suffix = if ($Version -match 'debug') { '' } else { '-debug' }
$distName = "Time-Manager-Android-$Version$suffix.apk"
Copy-Item "app\build\outputs\apk\debug\app-debug.apk" "dist\$distName" -Force
$size = [math]::Round((Get-Item "dist\$distName").Length / 1MB, 2)
Write-Host "==> APK: dist\$distName ($size MB)" -ForegroundColor Green

# ---------- 5. 提交并推送 ----------
git add -A
git commit -m $Message
if ($NoPush) {
    Write-Host "==> 已按 -NoPush 跳过提交后的推送（提交已在本地）" -ForegroundColor Yellow
} else {
    if ($BypassProxy) {
        $pushArgs = @("-c", "http.proxy=", "-c", "https.proxy=", "push", "origin", "main")
    } else {
        $pushArgs = @("push", "origin", "main")
    }
    # 本机到 github.com 的直连时好时坏（Recv failure / Could not resolve host），失败就退避重试
    $pushed = $false
    for ($i = 1; $i -le 6 -and -not $pushed; $i++) {
        if ($i -gt 1) { Write-Host "==> 推送第 $i 次尝试（等 $((6 * ($i - 1))) 秒）..." -ForegroundColor Yellow; Start-Sleep -Seconds (6 * ($i - 1)) }
        & git @pushArgs 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) { $pushed = $true }
    }
    if (-not $pushed) {
        Write-Host "!! 推送 6 次都失败了，但提交已经在本地，代码和 APK 都是好的。" -ForegroundColor Yellow
        Write-Host "   网络恢复后执行这一句补推：" -ForegroundColor Yellow
        Write-Host "   git -c http.proxy= -c https.proxy= push origin main" -ForegroundColor Yellow
    } else {
        Write-Host "==> 已推送到 GitHub" -ForegroundColor Green
    }
}
Write-Host ""
Write-Host "完成！CI 构建地址：" -ForegroundColor Green
Write-Host "  https://github.com/3507609128-hub/Time-Manager-Android/actions" -ForegroundColor Green
Write-Host "手机安装：把 dist\$distName 传到手机点击安装（或在项目目录执行）" -ForegroundColor Green
Write-Host "  adb install -r dist\$distName" -ForegroundColor Green
