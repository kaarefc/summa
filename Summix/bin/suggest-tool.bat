@setlocal
@echo off
rem $Id:$
rem
rem Windows BAT-equivalent to summa-storage.sh
rem

set BATLOCATION=%~dp0
pushd %BATLOCATION%
cd ..
set DEPLOY=%CD%

if "%CONFIGURATION%."=="." (
    set CONFIGURATION=%DEPLOY%/config/suggest-tool.configuration.xml
)

set ACTION=%1%
set QUERY=%2%
set HITCOUNT=%3%
set QUERYCOUNT=%4%

if "%ACTION%."=="." (
    echo USAGE:
    echo suggest-tool.sh action action_args
    echo Actions:
    echo query prefix
    echo update query hitcount [querycount]       
    echo clear
    echo import
    echo export
    echo delete
    goto :end
)

if "%ACTION%"=="query" (
    set ARGS=summa.support.suggest.prefix="%QUERY%"
    goto :afterIf
)
if "%ACTION%"=="update" (
    set ARGS=summa.support.suggest.update.query="%QUERY%"  summa.support.suggest.update.hitcount=%HITCOUNT%
    goto :actionOK
)
if "%ACTION%"=="clear" (
    set ARGS=summa.support.suggest.clear=true
    goto :actionOK
)
if "%ACTION%"=="import" (
    set ARGS=summa.support.suggest.import=true
    goto :actionOK
)
if "%ACTION%"=="export" (
    set ARGS=summa.support.suggest.export=true
    goto :actionOK
)
if "%ACTION%"=="delete" (
    set ARGS=summa.support.suggest.delete=true
    goto :actionOK
)
echo Unknown action %ACTION%
goto :end

:actionOK
if "%QUERYCOUNT%."=="." (
    goto :afterIf
)
set ARGS=%ARGS% summa.support.suggest.update.querycount=%QUERYCOUNT%

:afterIf

echo Calling search with %ARGS%
call bin\search-tool.bat %ARGS%

:end
popd
endlocal

