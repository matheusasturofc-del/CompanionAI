# CompanionAI (plugin para Paper 1.21.1)

Plugin de servidor: seu amigo não instala nada. Para ele, o servidor só tem
um "mod de cachorro com IA".

## Instalação
1. Use um servidor **Paper 1.21.1** (https://papermc.io).
2. Coloque `CompanionAI-1.0.0.jar` na pasta `plugins/` e reinicie.
3. Dê op só para você: `op SeuNick` no console. **Não dê op para o seu amigo**,
   senão ele vê o comando /horror e o painel.

## Comandos (só para op)
| Comando | O que faz |
|---|---|
| `/horror turn cachorro` | Vira um cachorro selvagem novo (ele pode te domesticar com ossos) |
| `/horror turn cachorro <nome>` | Entra num cachorro que já existe |
| `/horror sair` | Sai do cachorro (continua invisível) |
| `/horror rename <atual> to <novo>` | Renomeia um cachorro |
| `/horror monsterform [nome]` | Liga/desliga a forma de monstro |
| `/horror sentar` | Senta/levanta quando você está no cachorro |
| `/horror ver` | Mostra/esconde o cachorro na SUA tela (para se ver no F5) |
| `/horror vanish` | Some/aparece com mensagem falsa de "saiu/entrou no jogo" |
| `/horror painel` | Dá o item do painel (segure e clique com o botão ESQUERDO) |

## Como funciona
- Quando você vira cachorro, aparece "VocêNick saiu do jogo" e você some do TAB,
  do /list e da contagem de jogadores no menu de servidores.
- Tudo que você digita no chat aparece como `<NomeDoCachorro> mensagem` e num balão
  em cima do cachorro. Se você estiver invisível FORA do cachorro, seu chat é
  bloqueado para não te entregar.
- Ele pode clicar no cachorro para mandar sentar/levantar, dar comida e colocar coleira
  (a coleira te puxa de verdade).
- Quando ele domestica qualquer cachorro, abre uma tela para digitar o nome.
- Para "aparecer" no final: `/horror sair` e depois `/horror vanish`
  (sai a mensagem "VocêNick entrou no jogo" e você aparece do lado dele).

## Por que eu não me vejo como cachorro?
De propósito: na sua tela o cachorro fica escondido, senão a cabeça dele tapa a câmera.
Seu amigo vê o cachorro normalmente. Use `/horror ver` para se ver no F5.
O bonequinho do inventário sempre mostra o seu personagem (nenhum plugin consegue
trocar isso, só um mod no cliente).

## Configuração
`plugins/CompanionAI/config.yml`: formato do chat, nome padrão, tempo do balão,
chance de domesticar, tamanho do jogador em cada forma, rosnado do monstro.

## Compilar de novo
`gradle build` (Java 21). O jar sai em `build/libs/`.
