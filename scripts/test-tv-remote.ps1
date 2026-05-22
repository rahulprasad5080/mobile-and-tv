param(
    [string]$Serial = "",
    [string]$Package = "com.mplayer.videoplayer",
    [string]$Activity = "com.mplayer.videoplayer.mobile.MainActivity",
    [string]$Apk = "",
    [string]$Adb = "",
    [switch]$SkipInstall,
    [switch]$SkipLaunch,
    [switch]$AssumePlaybackReady,
    [int]$DelayMs = 700
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..")
if ([string]::IsNullOrWhiteSpace($Apk)) {
    $Apk = Join-Path $RepoRoot "app\build\outputs\apk\debug\app-debug.apk"
}
$OutDir = Join-Path $RepoRoot "tv-remote-test-output"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Get-LocalSdkDir {
    $localProperties = Join-Path $RepoRoot "local.properties"
    if (-not (Test-Path $localProperties)) {
        return $null
    }

    $line = Get-Content $localProperties |
        Where-Object { $_ -match "^\s*sdk\.dir\s*=" } |
        Select-Object -First 1
    if (-not $line) {
        return $null
    }

    $rawValue = ($line -split "=", 2)[1].Trim()
    return $rawValue.Replace("\:", ":").Replace("\\", "\")
}

function Resolve-AdbPath {
    if (-not [string]::IsNullOrWhiteSpace($Adb)) {
        if (Test-Path $Adb) {
            return (Resolve-Path $Adb).Path
        }
        throw "ADB path not found: $Adb"
    }

    $pathAdb = Get-Command adb -ErrorAction SilentlyContinue
    if ($pathAdb) {
        return $pathAdb.Source
    }

    $sdkCandidates = @(
        (Get-LocalSdkDir),
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        (Join-Path $env:LOCALAPPDATA "Android\Sdk")
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique

    foreach ($sdk in $sdkCandidates) {
        $candidate = Join-Path $sdk "platform-tools\adb.exe"
        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw "adb.exe not found. Install Android SDK Platform Tools, add adb to PATH, or pass -Adb `"C:\path\to\adb.exe`"."
}

$AdbCommand = Resolve-AdbPath
Write-Host "Using adb: $AdbCommand"

function Get-AdbPrefix {
    if ([string]::IsNullOrWhiteSpace($Serial)) {
        return @()
    }
    return @("-s", $Serial)
}

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs)
    $prefix = Get-AdbPrefix
    & $script:AdbCommand @prefix @AdbArgs
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($AdbArgs -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Invoke-AdbBestEffort {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs)
    $prefix = Get-AdbPrefix
    & $script:AdbCommand @prefix @AdbArgs 2>$null | Out-Null
}

function Assert-AdbDevice {
    $deviceLines = & $script:AdbCommand devices | Select-String "`tdevice$"
    if (-not $deviceLines) {
        throw "No adb device/emulator found. Start an Android TV emulator or connect a device with USB debugging."
    }
    if ([string]::IsNullOrWhiteSpace($Serial) -and $deviceLines.Count -gt 1) {
        throw "Multiple adb devices found. Re-run with -Serial <deviceSerial>."
    }
}

function Save-State {
    param(
        [int]$Index,
        [string]$Name
    )

    $safeName = $Name -replace "[^a-zA-Z0-9_-]", "_"
    $xmlPath = Join-Path $OutDir ("{0:00}-{1}.xml" -f $Index, $safeName)
    $pngPath = Join-Path $OutDir ("{0:00}-{1}.png" -f $Index, $safeName)

    Invoke-AdbBestEffort shell uiautomator dump /sdcard/window.xml
    Invoke-AdbBestEffort pull /sdcard/window.xml $xmlPath
    Invoke-AdbBestEffort shell screencap -p ("/sdcard/{0}.png" -f $safeName)
    Invoke-AdbBestEffort pull ("/sdcard/{0}.png" -f $safeName) $pngPath

    $focusedSummary = "focused node unavailable"
    if (Test-Path $xmlPath) {
        try {
            [xml]$xml = Get-Content $xmlPath
            $focused = Select-Xml -Xml $xml -XPath "//*[@focused='true']" |
                Select-Object -First 1 -ExpandProperty Node
            if ($focused) {
                $focusedSummary = "class='$($focused.GetAttribute("class"))' text='$($focused.GetAttribute("text"))' desc='$($focused.GetAttribute("content-desc"))' bounds='$($focused.GetAttribute("bounds"))'"
            }
        } catch {
            $focusedLine = Select-String -Path $xmlPath -Pattern 'focused="true"' | Select-Object -First 1
            if ($focusedLine) {
                $focusedSummary = $focusedLine.Line.Trim()
            }
        }
    }

    "{0:00},{1},{2}" -f $Index, $Name, $focusedSummary |
        Add-Content -Path (Join-Path $OutDir "remote-sequence.csv")
    Write-Host ("[{0:00}] {1}: {2}" -f $Index, $Name, $focusedSummary)
}

Assert-AdbDevice

if (-not $SkipInstall) {
    if (-not (Test-Path $Apk)) {
        Push-Location $RepoRoot
        try {
            & .\gradlew.bat assembleDebug
            if ($LASTEXITCODE -ne 0) {
                throw "Gradle assembleDebug failed with exit code $LASTEXITCODE"
            }
        } finally {
            Pop-Location
        }
    }
    Invoke-Adb install -r $Apk
}

Invoke-AdbBestEffort shell pm grant $Package android.permission.READ_EXTERNAL_STORAGE
Invoke-AdbBestEffort shell pm grant $Package android.permission.READ_MEDIA_VIDEO
if (-not $SkipLaunch) {
    Invoke-Adb shell am force-stop $Package
    Invoke-Adb shell am start -n "$Package/$Activity"
    Start-Sleep -Seconds 3
}

if (-not $AssumePlaybackReady) {
    Write-Host ""
    Write-Host "Open any video in the app so the TV player screen is visible."
    Write-Host "Then press Enter here. The script will replay DPAD keys and save screenshots/XML."
    [void][Console]::ReadLine()
}

Remove-Item -Path (Join-Path $OutDir "remote-sequence.csv") -ErrorAction SilentlyContinue
"step,name,focused" | Set-Content -Path (Join-Path $OutDir "remote-sequence.csv")

$steps = @(
    @{ Name = "initial_player_state"; Key = "" },
    @{ Name = "show_controls_center"; Key = "KEYCODE_DPAD_CENTER" },
    @{ Name = "up_to_top_bar"; Key = "KEYCODE_DPAD_UP" },
    @{ Name = "right_1_should_leave_back"; Key = "KEYCODE_DPAD_RIGHT" },
    @{ Name = "right_2_should_reach_audio_or_subtitle"; Key = "KEYCODE_DPAD_RIGHT" },
    @{ Name = "right_3_should_reach_subtitle_or_settings"; Key = "KEYCODE_DPAD_RIGHT" },
    @{ Name = "open_focused_top_control"; Key = "KEYCODE_DPAD_CENTER" },
    @{ Name = "close_popup_or_dialog"; Key = "KEYCODE_BACK" },
    @{ Name = "down_to_player_controls"; Key = "KEYCODE_DPAD_DOWN" },
    @{ Name = "left_between_player_controls"; Key = "KEYCODE_DPAD_LEFT" },
    @{ Name = "right_between_player_controls"; Key = "KEYCODE_DPAD_RIGHT" },
    @{ Name = "media_rewind_still_seeks"; Key = "KEYCODE_MEDIA_REWIND" },
    @{ Name = "media_fast_forward_still_seeks"; Key = "KEYCODE_MEDIA_FAST_FORWARD" }
)

$index = 0
foreach ($step in $steps) {
    if (-not [string]::IsNullOrWhiteSpace($step.Key)) {
        Invoke-Adb shell input keyevent $step.Key
        Start-Sleep -Milliseconds $DelayMs
    }
    Save-State -Index $index -Name $step.Name
    $index++
}

Write-Host ""
Write-Host "TV remote replay complete."
Write-Host "Artifacts: $OutDir"
Write-Host "Pass check: after the right_* steps, focus should move away from Back/Play-Pause toward Audio, Subtitles, or Settings. Screenshots and remote-sequence.csv show the result."
