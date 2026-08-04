@echo off
@rem ---------------------------------------------------------------------------
@rem IntelliRelease Maven bootstrap (Windows)
@rem
@rem Maven is not assumed to be installed. On first run this downloads Apache
@rem Maven into backend/.mvn/ and delegates to it. Subsequent runs are instant.
@rem   Usage:  mvnw.cmd clean package
@rem ---------------------------------------------------------------------------
setlocal

set "MAVEN_VERSION=3.9.9"
set "MVN_ROOT=%~dp0.mvn"
set "MVN_HOME=%MVN_ROOT%\apache-maven-%MAVEN_VERSION%"
set "MVN_CMD=%MVN_HOME%\bin\mvn.cmd"

if exist "%MVN_CMD%" goto run

echo [mvnw] Apache Maven %MAVEN_VERSION% not found locally. Downloading...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$url='https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip';" ^
  "$zip=Join-Path $env:TEMP ('apache-maven-%MAVEN_VERSION%-bin.zip');" ^
  "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;" ^
  "Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing;" ^
  "New-Item -ItemType Directory -Force -Path '%MVN_ROOT%' ^| Out-Null;" ^
  "Expand-Archive -Path $zip -DestinationPath '%MVN_ROOT%' -Force;" ^
  "Remove-Item $zip -Force"

if not exist "%MVN_CMD%" (
  echo [mvnw] ERROR: could not bootstrap Maven. Install Maven manually and re-run.
  exit /b 1
)

:run
call "%MVN_CMD%" %*
