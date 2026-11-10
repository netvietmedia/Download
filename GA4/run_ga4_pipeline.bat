@echo off
chcp 65001 >nul
echo ======================================================================
echo  SHARENOW - TIEN TRINH KEO DU LIEU GA4 VA TOI UU DOANH THU MULTI-CHANNEL
echo ======================================================================
echo.

cd /d "%~dp0"

echo [1/3] Dang chay kich ban keo du lieu tu GA4 / Firebase...
python pull_ga4_events.py
if %ERRORLEVEL% NEQ 0 (
    echo [!] Gap loi trong qua trinh keo du lieu tu GA4.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/3] Dang phan tich eCPM, ROAS va toi uu hoa chien dich quang cao...
python revenue_optimizer.py
if %ERRORLEVEL% NEQ 0 (
    echo [!] Gap loi trong qua trinh phan tich toi uu.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [3/3] Dang tao Dashboard HTML truc quan tuong tac...
python generate_dashboard.py
if %ERRORLEVEL% NEQ 0 (
    echo [!] Gap loi trong qua trinh tao Dashboard.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ======================================================================
echo  HOAN TAT! DANG MO DASHBOARD TREN TRINH DUYET...
echo ======================================================================
start dashboard.html
pause

