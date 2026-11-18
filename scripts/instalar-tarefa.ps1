# Cria a tarefa "Gerenciador de Contas por E-mail" no Agendador de Tarefas do Windows.
# Uso (PowerShell, na raiz do projeto, depois de "mvn package"):
#   mkdir C:\GerenciadorDeContas
#   copy target\gerenciadordecontas.jar C:\GerenciadorDeContas\
#   copy .env, apelidos.json e a pasta secrets para C:\GerenciadorDeContas\
#   .\scripts\instalar-tarefa.ps1
param(
    [string]$Pasta = "C:\GerenciadorDeContas"
)

$jar = Join-Path $Pasta "gerenciadordecontas.jar"
if (-not (Test-Path $jar)) { throw "Não achei $jar. Rode 'mvn package' e copie o jar para a pasta." }

$java = Get-Command javaw.exe -ErrorAction SilentlyContinue
if (-not $java) { throw "Não achei o javaw.exe. Instale o Java 17 e confira o PATH." }

# javaw: roda sem abrir janela de console.
$acao = New-ScheduledTaskAction -Execute $java.Source -Argument "-jar `"$jar`" tick" -WorkingDirectory $Pasta

# Ao entrar no Windows e 3 vezes por dia.
$gatilhos = @(
    (New-ScheduledTaskTrigger -AtLogOn),
    (New-ScheduledTaskTrigger -Daily -At "08:00"),
    (New-ScheduledTaskTrigger -Daily -At "13:00"),
    (New-ScheduledTaskTrigger -Daily -At "20:00")
)

# StartWhenAvailable: se o PC estava desligado na hora, roda assim que possível.
$config = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 5)

Register-ScheduledTask -TaskName "Gerenciador de Contas por E-mail" -Action $acao -Trigger $gatilhos -Settings $config `
    -Description "Avisos de contas por e-mail e baixa por resposta" -Force | Out-Null

Write-Host "Tarefa criada (Gerenciador de Contas por E-mail): ao entrar no Windows e às 08:00, 13:00 e 20:00."
