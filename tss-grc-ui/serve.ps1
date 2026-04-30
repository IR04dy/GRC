# Simple static file server for TSS GRC UI
# Usage: pwsh -File serve.ps1
# Serves tss-grc-ui/ at http://localhost:8086

$port = 8086
$root = $PSScriptRoot   # directory containing this script (tss-grc-ui)

$mimeTypes = @{
    '.html' = 'text/html; charset=utf-8'
    '.css'  = 'text/css; charset=utf-8'
    '.js'   = 'application/javascript; charset=utf-8'
    '.json' = 'application/json'
    '.png'  = 'image/png'
    '.jpg'  = 'image/jpeg'
    '.svg'  = 'image/svg+xml'
    '.ico'  = 'image/x-icon'
    '.woff2'= 'font/woff2'
    '.woff' = 'font/woff'
    '.ttf'  = 'font/ttf'
    '.map'  = 'application/json'
}

$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add("http://localhost:$port/")
$listener.Start()

Write-Host ""
Write-Host "  TSS GRC UI  ->  http://localhost:$port" -ForegroundColor Cyan
Write-Host "  Press Ctrl+C to stop" -ForegroundColor DarkGray
Write-Host ""

try {
    while ($listener.IsListening) {
        $ctx = $listener.GetContext()
        $req = $ctx.Request
        $res = $ctx.Response

        $urlPath = $req.Url.LocalPath
        # Default to index.html
        if ($urlPath -eq '/') { $urlPath = '/index.html' }

        $filePath = Join-Path $root ($urlPath.TrimStart('/').Replace('/', [IO.Path]::DirectorySeparatorChar))

        # If directory requested, serve index.html inside it
        if (Test-Path $filePath -PathType Container) {
            $filePath = Join-Path $filePath 'index.html'
        }

        if (Test-Path $filePath -PathType Leaf) {
            $ext  = [IO.Path]::GetExtension($filePath).ToLower()
            $mime = if ($mimeTypes.ContainsKey($ext)) { $mimeTypes[$ext] } else { 'application/octet-stream' }
            $bytes = [IO.File]::ReadAllBytes($filePath)

            $res.ContentType   = $mime
            $res.ContentLength64 = $bytes.Length
            $res.StatusCode    = 200
            $res.OutputStream.Write($bytes, 0, $bytes.Length)
        } else {
            # 404
            $body  = [Text.Encoding]::UTF8.GetBytes("404 Not Found: $urlPath")
            $res.StatusCode    = 404
            $res.ContentType   = 'text/plain'
            $res.ContentLength64 = $body.Length
            $res.OutputStream.Write($body, 0, $body.Length)
        }
        $res.OutputStream.Close()

        $color = if ($res.StatusCode -eq 200) { 'Green' } else { 'Yellow' }
        Write-Host "  $($res.StatusCode)  $urlPath" -ForegroundColor $color
    }
} finally {
    $listener.Stop()
    Write-Host "Server stopped." -ForegroundColor DarkGray
}
