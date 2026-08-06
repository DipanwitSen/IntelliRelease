# Loads backend\.env (gitignored - see .env for the template) into the process
# environment, then starts the Spring Boot backend on the dev profile.
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
            $key, $value = $line.Split('=', 2)
            $key = $key.Trim()
            $value = $value.Trim()
            if ($value) {
                [Environment]::SetEnvironmentVariable($key, $value, 'Process')
            }
        }
    }
    Write-Host "Loaded $envFile"
} else {
    Write-Host "No .env file found at $envFile - starting with defaults (MailHog only, no Outlook/Teams/GitHub)."
}

& "$PSScriptRoot\mvnw.cmd" spring-boot:run "-Dspring-boot.run.profiles=dev"
