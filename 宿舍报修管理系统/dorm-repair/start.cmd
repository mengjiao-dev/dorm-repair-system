@echo off
chcp 65001 >nul
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
 echo 未找到 Java。请先安装 Java 17 或更高版本。
 pause
 exit /b 1
)
echo 宿舍报修管理系统正在启动，请保留此窗口。
echo 看到 Started Application 后，在浏览器打开：http://localhost:18080
echo 演示账号：student / manager / worker / admin
echo 演示密码：Demo@2026
echo 关闭此窗口或按 Ctrl+C 可停止系统。
java -jar dorm-repair.jar --server.port=18080
pause
