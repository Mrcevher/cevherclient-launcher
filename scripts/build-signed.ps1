# İmzalı Windows kurulumu üretir. Sertifika şifresi dosyaya veya komut satırına yazılmaz:
# yalnızca bu oturumun ortam değişkeninde, derleme bitince silinir.
#
# Kullanım:  powershell -ExecutionPolicy Bypass -File scripts\build-signed.ps1 -Pfx "C:\yol\sertifika.pfx"
param([Parameter(Mandatory = $true)][string]$Pfx)

if (-not (Test-Path -LiteralPath $Pfx)) { throw "Sertifika bulunamadı: $Pfx" }
$secure = Read-Host "Sertifika şifresi" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
  $env:CSC_LINK = (Resolve-Path -LiteralPath $Pfx).Path
  $env:CSC_KEY_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
  npm run build:win
  if ($LASTEXITCODE -ne 0) { throw "Derleme başarısız" }
  Get-ChildItem dist -Filter 'Cevherclient-Setup-*.exe' | ForEach-Object {
    $sig = Get-AuthenticodeSignature $_.FullName
    "{0}: {1} ({2})" -f $_.Name, $sig.Status, $sig.SignerCertificate.Subject
  }
} finally {
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
  Remove-Item Env:CSC_LINK, Env:CSC_KEY_PASSWORD -ErrorAction SilentlyContinue
}
