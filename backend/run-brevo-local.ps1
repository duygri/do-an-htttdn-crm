[CmdletBinding()]
param(
    [switch]$CheckOnly,
    [ValidateRange(1024, 65535)][int]$Port = 8082,
    [ValidatePattern('^[^@\s]+@[^@\s]+\.[^@\s]+$')][string]$TestEmail = 'anhkiet240205@gmail.com'
)

$ErrorActionPreference = 'Stop'
$settings = @{
    MAIL_PROVIDER = 'brevo'
    BREVO_ENABLED = 'true'
    BREVO_SENDER_EMAIL = $TestEmail
    BREVO_SENDER_NAME = 'ANH LON SHOP'
    BREVO_TEST_MODE = 'true'
    BREVO_TEST_RECIPIENT = $TestEmail
    PUBLIC_ORIGIN = 'https://anhlonshop.onrender.com'
    DEV_PASSWORD_RESET_ENABLED = 'false'
    SERVER_PORT = [string]$Port
    SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
    PAYOS_AUTO_CANCEL_ENABLED = 'false'
}
$previous = @{}
foreach ($name in $settings.Keys) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$temporarySecrets = [System.Collections.Generic.List[string]]::new()
function Set-SessionSecret {
    param([string]$Name, [string]$Prompt)
    if (-not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($Name, 'Process'))) { return }
    $secureValue = Read-Host $Prompt -AsSecureString
    $pointer = [IntPtr]::Zero
    try {
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
        $plainValue = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
        if ([string]::IsNullOrWhiteSpace($plainValue)) { throw "$Name khong duoc de trong." }
        [Environment]::SetEnvironmentVariable($Name, $plainValue, 'Process')
        $temporarySecrets.Add($Name)
    }
    finally {
        $plainValue = $null
        if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
        $secureValue.Dispose()
    }
}
try {
    $maven = Get-Command mvn -ErrorAction Stop
    $null = Get-Command java -ErrorAction Stop
    foreach ($name in $settings.Keys) { [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process') }
    Write-Host "Brevo local: backend http://localhost:$Port; chi gui thu toi $TestEmail."
    Write-Warning 'Chi kiem tra nhan email. Lien ket reset tro ve Render, KHONG dung lien ket nay de doi mat khau tai khoan local.'
    Write-Host 'Giu cau hinh DB hien co; validate schema, khong chay migration. Khong tu huy PayOS.'
    if ($CheckOnly) {
        Write-Host 'Cau hinh va cong cu hop le; chua xac minh ket noi DB hay bi mat. Chua khoi dong backend, chua doc API key, chua gui email.'
        return
    }
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable('JWT_SECRET', 'Process'))) {
        $randomBytes = New-Object byte[] 32
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $random.GetBytes($randomBytes)
            [Environment]::SetEnvironmentVariable('JWT_SECRET', [Convert]::ToBase64String($randomBytes), 'Process')
            $temporarySecrets.Add('JWT_SECRET')
        }
        finally {
            [Array]::Clear($randomBytes, 0, $randomBytes.Length)
            $random.Dispose()
        }
        Write-Warning 'Da tao JWT secret ngau nhien cho phien local nay. Can dang nhap lai; khong dung cho Render/production.'
    }
    Set-SessionSecret -Name 'DB_PASSWORD' -Prompt 'Nhap mat khau PostgreSQL local dang dung (an ky tu)'
    Set-SessionSecret -Name 'BREVO_API_KEY' -Prompt 'Nhap Brevo API key (an ky tu; khong luu vao file)'
    Push-Location $PSScriptRoot
    try {
        & $maven 'spring-boot:run'
        if ($LASTEXITCODE -ne 0) { throw "Backend dung voi ma loi $LASTEXITCODE. Xem log phia tren (khong chia se API key)." }
    }
    finally { Pop-Location }
}
finally {
    foreach ($name in $settings.Keys) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    foreach ($name in $temporarySecrets) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
}
