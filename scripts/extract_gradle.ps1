Add-Type -AssemblyName System.IO.Compression.FileSystem
$out=Join-Path $env:TEMP 'gradle-8.7-bin.zip'
$dest=Join-Path $env:LOCALAPPDATA 'led-matin-gradle'
if(Test-Path $dest){Remove-Item $dest -Recurse -Force}
New-Item -ItemType Directory -Path $dest | Out-Null
[System.IO.Compression.ZipFile]::ExtractToDirectory($out,$dest)
Write-Output "Extracted to $dest"
Get-ChildItem $dest | Select-Object -First 10
