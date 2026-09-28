@echo off
setlocal EnableExtensions

set "EASYBPM_ROOT=%~dp0"
if "%EASYBPM_ROOT:~-1%"=="\" set "EASYBPM_ROOT=%EASYBPM_ROOT:~0,-1%"

if /I "%~1"=="--run-backend" goto :run_backend
if /I "%~1"=="--run-worker" goto :run_worker
if /I "%~1"=="--run-modeler" goto :run_modeler
if /I "%~1"=="--run-admin" goto :run_admin
if /I "%~1"=="--run-task-portal" goto :run_task_portal
if /I "%~1"=="--dry-run" goto :dry_run

echo.
echo EasyBPM - ambiente local
echo =========================
echo.

call :require_command java "Java 21"
if errorlevel 1 exit /b 1

call :require_command node "Node.js 22+"
if errorlevel 1 exit /b 1

call :require_command npm "npm"
if errorlevel 1 exit /b 1

if not exist "%EASYBPM_ROOT%\gradlew.bat" (
  echo [ERRO] gradlew.bat nao foi encontrado em "%EASYBPM_ROOT%".
  exit /b 1
)

call :require_directory "%EASYBPM_ROOT%\easy-bpm-modeler" "Modeler"
if errorlevel 1 exit /b 1

call :require_directory "%EASYBPM_ROOT%\easy-bpm-admin" "Admin"
if errorlevel 1 exit /b 1

call :require_directory "%EASYBPM_ROOT%\easy-bpm-task-portal" "Task Portal"
if errorlevel 1 exit /b 1

echo Verificando PostgreSQL e RabbitMQ executados pelo Docker...
call :check_port 5432 "PostgreSQL"
if errorlevel 1 set "INFRA_MISSING=1"

call :check_port 5672 "RabbitMQ"
if errorlevel 1 set "INFRA_MISSING=1"

if defined INFRA_MISSING (
  echo.
  echo [ERRO] Inicie a infraestrutura antes de executar este arquivo:
  echo        docker compose up -d postgres rabbitmq
  echo.
  exit /b 1
)

call :ensure_dependencies "%EASYBPM_ROOT%\easy-bpm-modeler" "Modeler"
if errorlevel 1 exit /b 1

call :ensure_dependencies "%EASYBPM_ROOT%\easy-bpm-admin" "Admin"
if errorlevel 1 exit /b 1

call :ensure_dependencies "%EASYBPM_ROOT%\easy-bpm-task-portal" "Task Portal"
if errorlevel 1 exit /b 1

echo.
echo Iniciando os servicos em janelas separadas...

start "EasyBPM Backend" %ComSpec% /d /k call "%~f0" --run-backend
start "EasyBPM Worker" %ComSpec% /d /k call "%~f0" --run-worker
start "EasyBPM Modeler" %ComSpec% /d /k call "%~f0" --run-modeler
start "EasyBPM Admin" %ComSpec% /d /k call "%~f0" --run-admin
start "EasyBPM Task Portal" %ComSpec% /d /k call "%~f0" --run-task-portal

echo.
echo Aplicacoes iniciadas:
echo   Backend API : http://localhost:8080
echo   Swagger     : http://localhost:8080/swagger-ui.html
echo   Modeler     : http://localhost:3000
echo   Admin       : http://localhost:3001
echo   Task Portal : http://localhost:3002
echo.
echo Feche as cinco janelas abertas para encerrar as aplicacoes locais.
exit /b 0

:require_command
where %~1 >nul 2>&1
if errorlevel 1 (
  echo [ERRO] %~2 nao foi encontrado no PATH.
  exit /b 1
)
exit /b 0

:require_directory
if not exist "%~1\package.json" (
  echo [ERRO] O projeto %~2 nao foi encontrado em "%~1".
  exit /b 1
)
exit /b 0

:check_port
powershell.exe -NoProfile -NonInteractive -Command "$client = [System.Net.Sockets.TcpClient]::new(); try { $task = $client.ConnectAsync('127.0.0.1', %~1); if (-not $task.Wait(1000) -or -not $client.Connected) { exit 1 }; exit 0 } catch { exit 1 } finally { $client.Dispose() }" >nul 2>&1
if errorlevel 1 (
  echo [FALHA] %~2 nao esta acessivel em localhost:%~1.
  exit /b 1
)
echo [OK] %~2 esta acessivel em localhost:%~1.
exit /b 0

:ensure_dependencies
if exist "%~1\node_modules" exit /b 0
echo.
echo Instalando dependencias do %~2 pela primeira vez...
pushd "%~1"
call npm install
if errorlevel 1 (
  popd
  echo [ERRO] Nao foi possivel instalar as dependencias do %~2.
  exit /b 1
)
popd
exit /b 0

:run_backend
title EasyBPM Backend
cd /d "%EASYBPM_ROOT%"
echo Iniciando EasyBPM Backend em http://localhost:8080 ...
if defined EASYBPM_LAUNCHER_SELF_TEST exit /b 0
call gradlew.bat bootRun
exit /b %ERRORLEVEL%

:run_worker
title EasyBPM Worker
cd /d "%EASYBPM_ROOT%"
echo Iniciando EasyBPM Worker ...
if defined EASYBPM_LAUNCHER_SELF_TEST exit /b 0
call gradlew.bat :worker:bootRun
exit /b %ERRORLEVEL%

:run_modeler
title EasyBPM Modeler - localhost:3000
cd /d "%EASYBPM_ROOT%\easy-bpm-modeler"
echo Iniciando EasyBPM Modeler em http://localhost:3000 ...
if defined EASYBPM_LAUNCHER_SELF_TEST exit /b 0
call npm run dev
exit /b %ERRORLEVEL%

:run_admin
title EasyBPM Admin - localhost:3001
cd /d "%EASYBPM_ROOT%\easy-bpm-admin"
echo Iniciando EasyBPM Admin em http://localhost:3001 ...
if defined EASYBPM_LAUNCHER_SELF_TEST exit /b 0
call npm run dev
exit /b %ERRORLEVEL%

:run_task_portal
title EasyBPM Task Portal - localhost:3002
cd /d "%EASYBPM_ROOT%\easy-bpm-task-portal"
echo Iniciando EasyBPM Task Portal em http://localhost:3002 ...
if defined EASYBPM_LAUNCHER_SELF_TEST exit /b 0
call npm run dev
exit /b %ERRORLEVEL%

:dry_run
echo EasyBPM - verificacao do iniciador local
echo.
echo Raiz        : %EASYBPM_ROOT%
echo Backend     : gradlew.bat bootRun
echo Worker      : gradlew.bat :worker:bootRun
echo Modeler     : npm run dev ^(http://localhost:3000^)
echo Admin       : npm run dev ^(http://localhost:3001^)
echo Task Portal : npm run dev ^(http://localhost:3002^)
echo Infra Docker: PostgreSQL 5432 e RabbitMQ 5672
exit /b 0
