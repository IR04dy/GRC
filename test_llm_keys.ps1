[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "         TSS GRC Platform - LLM Keys Diagnostics          " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# -----------------------------------------------------------------------------
# 1. TEST OPENAI
# -----------------------------------------------------------------------------
Write-Host ""
Write-Host "[1/3] Testing OpenAI (gpt-4o-mini)..." -ForegroundColor Yellow

$openaiKey = $env:OPENAI_API_KEY
if ([string]::IsNullOrWhiteSpace($openaiKey)) {
    Write-Host "  FAILED: OPENAI_API_KEY is not set in this shell." -ForegroundColor DarkGray
} else {
    $maskedKey = $openaiKey.Substring(0, [Math]::Min(10, $openaiKey.Length)) + "..."
    Write-Host "  Found key: $maskedKey" -ForegroundColor Gray

    $headers = @{
        "Authorization" = "Bearer $openaiKey"
        "Content-Type"  = "application/json"
    }
    $body = '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"ping"}],"max_tokens":10}'

    try {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $res = Invoke-RestMethod -Uri "https://api.openai.com/v1/chat/completions" -Method Post -Headers $headers -Body $body -TimeoutSec 15
        $sw.Stop()
        $content = $res.choices[0].message.content.Trim()
        $ms = $sw.ElapsedMilliseconds
        Write-Host "  SUCCESS! Response: $content ($ms ms)" -ForegroundColor Green
    } catch {
        Write-Host "  FAILED!" -ForegroundColor Red
        if ($_.Exception.Response) {
            $code = $_.Exception.Response.StatusCode.value__
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $raw = $reader.ReadToEnd()
            Write-Host "  HTTP Status: $code" -ForegroundColor Red
            Write-Host "  Details: $raw" -ForegroundColor Red
        } else {
            Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

# -----------------------------------------------------------------------------
# 2. TEST GOOGLE GEMINI
# -----------------------------------------------------------------------------
Write-Host ""
Write-Host "[2/3] Testing Google Gemini..." -ForegroundColor Yellow

$geminiKey = $env:GEMINI_API_KEY
if ([string]::IsNullOrWhiteSpace($geminiKey)) {
    Write-Host "  FAILED: GEMINI_API_KEY is not set in this shell." -ForegroundColor DarkGray
} else {
    $maskedKey = $geminiKey.Substring(0, [Math]::Min(10, $geminiKey.Length)) + "..."
    Write-Host "  Found key: $maskedKey" -ForegroundColor Gray

    $modelsToTry = @("gemini-1.5-flash", "gemini-2.5-flash", "gemini-flash-latest")
    $geminiBody = '{"contents":[{"role":"user","parts":[{"text":"ping"}]}]}'

    foreach ($m in $modelsToTry) {
        Write-Host "  Trying model: $m ..." -ForegroundColor Gray
        try {
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            $url = "https://generativelanguage.googleapis.com/v1beta/models/" + $m + ":generateContent?key=" + $geminiKey
            $res = Invoke-RestMethod -Uri $url -Method Post -Body $geminiBody -ContentType "application/json" -TimeoutSec 15
            $sw.Stop()
            $text = $res.candidates[0].content.parts[0].text.Trim()
            $ms = $sw.ElapsedMilliseconds
            Write-Host "  SUCCESS ($m)! Response: $text ($ms ms)" -ForegroundColor Green
            break
        } catch {
            if ($_.Exception.Response) {
                $code = $_.Exception.Response.StatusCode.value__
                $stream = $_.Exception.Response.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($stream)
                $raw = $reader.ReadToEnd()
                Write-Host "    $m failed: HTTP $code - $raw" -ForegroundColor DarkYellow
            } else {
                Write-Host "    $m failed: $($_.Exception.Message)" -ForegroundColor DarkYellow
            }
        }
    }
}

# -----------------------------------------------------------------------------
# 3. TEST Z.AI (GLM)
# -----------------------------------------------------------------------------
Write-Host ""
Write-Host "[3/3] Testing Z.ai (GLM)..." -ForegroundColor Yellow

$glmKey = $env:GLM_API_KEY
if ([string]::IsNullOrWhiteSpace($glmKey)) {
    Write-Host "  FAILED: GLM_API_KEY is not set in this shell." -ForegroundColor DarkGray
} else {
    $maskedKey = $glmKey.Substring(0, [Math]::Min(10, $glmKey.Length)) + "..."
    Write-Host "  Found key: $maskedKey" -ForegroundColor Gray

    $headers = @{
        "Authorization" = "Bearer $glmKey"
        "Content-Type"  = "application/json"
    }

    $endpoints = @(
        @{ Name = "Z.ai Standard PaaS"; Url = "https://api.z.ai/api/paas/v4/chat/completions"; Models = @("glm-4.7", "glm-4-plus", "glm-4.5") },
        @{ Name = "Z.ai Coding Plan";   Url = "https://api.z.ai/api/coding/paas/v4/chat/completions"; Models = @("glm-4.7", "glm-5", "glm-4.5") }
    )

    $success = $false
    foreach ($ep in $endpoints) {
        $epName = $ep.Name
        $epUrl = $ep.Url
        Write-Host "  Checking: $epName" -ForegroundColor Gray

        foreach ($m in $ep.Models) {
            $body = '{"model":"' + $m + '","messages":[{"role":"user","content":"ping"}],"max_tokens":10}'

            try {
                $sw = [System.Diagnostics.Stopwatch]::StartNew()
                $res = Invoke-RestMethod -Uri $epUrl -Method Post -Headers $headers -Body $body -TimeoutSec 15
                $sw.Stop()
                $content = $res.choices[0].message.content.Trim()
                $ms = $sw.ElapsedMilliseconds
                Write-Host "  SUCCESS! ($epName / $m): $content ($ms ms)" -ForegroundColor Green
                $success = $true
                break
            } catch {
                if ($_.Exception.Response) {
                    $code = $_.Exception.Response.StatusCode.value__
                    $stream = $_.Exception.Response.GetResponseStream()
                    $reader = New-Object System.IO.StreamReader($stream)
                    $raw = $reader.ReadToEnd()
                    Write-Host "    $epName ($m): HTTP $code - $raw" -ForegroundColor DarkYellow
                } else {
                    Write-Host "    $epName ($m): $($_.Exception.Message)" -ForegroundColor DarkYellow
                }
            }
        }
        if ($success) { break }
    }
}

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "                 Diagnostics Complete                     " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
