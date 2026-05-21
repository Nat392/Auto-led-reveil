$url='https://services.gradle.org/distributions/gradle-8.7-bin.zip'
$out=Join-Path $env:TEMP 'gradle-8.7-bin.zip'
if(Test-Path $out){Remove-Item $out -Force}
Write-Output "Downloading to $out"
$wc=New-Object System.Net.WebClient
$wc.DownloadFile($url,$out)
Write-Output (Get-Item $out).Length
