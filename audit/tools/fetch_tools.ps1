$ErrorActionPreference = 'Stop'
$dest = 'D:\AYMAN\GRC\audit\tools'
function Get-LatestAsset($repo, $pattern, $outzip, $exeName) {
    try {
        $rel = Invoke-RestMethod "https://api.github.com/repos/$repo/releases/latest" -Headers @{ 'User-Agent'='audit' }
        $asset = $rel.assets | Where-Object { $_.name -like $pattern } | Select-Object -First 1
        if (-not $asset) { Write-Output "[$repo] no asset matching $pattern"; return }
        $zip = Join-Path $dest $outzip
        Invoke-WebRequest $asset.browser_download_url -OutFile $zip -Headers @{ 'User-Agent'='audit' }
        $ex = Join-Path $dest ($repo.Split('/')[-1])
        Expand-Archive -Path $zip -DestinationPath $ex -Force
        $exe = Get-ChildItem $ex -Recurse -Filter $exeName | Select-Object -First 1
        if ($exe) { Copy-Item $exe.FullName (Join-Path $dest $exeName) -Force; Write-Output "[$repo] OK -> $exeName ($($rel.tag_name))" }
        else { Write-Output "[$repo] downloaded but $exeName not found" }
    } catch { Write-Output "[$repo] FAILED: $($_.Exception.Message)" }
}
Get-LatestAsset 'aquasecurity/trivy' '*windows-64bit.zip'  'trivy.zip'    'trivy.exe'
Get-LatestAsset 'gitleaks/gitleaks'  '*windows_x64.zip'    'gitleaks.zip' 'gitleaks.exe'
Get-LatestAsset 'grafana/k6'         '*windows-amd64.zip'  'k6.zip'       'k6.exe'
Write-Output 'DONE'
