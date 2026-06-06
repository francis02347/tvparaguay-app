Set shell = CreateObject("WScript.Shell")
shell.Run "cmd.exe /c python -u telegram_developer_bot.py > bot_log.txt 2>&1", 0, False
Set shell = Nothing
