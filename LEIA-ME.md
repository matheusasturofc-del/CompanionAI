# CompanionAI (plugin para Paper 1.21.1)

Plugin de servidor: seu amigo não instala nada. Para ele, o servidor só tem
um "mod de cachorro com IA".

## Instalação
1. Use um servidor **Paper 1.21.1** (https://papermc.io).
2. Coloque `CompanionAI-1.0.0.jar` na pasta `plugins/` e reinicie.
3. Dê op só para você: `op SeuNick` no console. **Não dê op para o seu amigo**,
   senão ele vê o comando /horror e o painel.

## Comandos (só para op)
Digite `/horror` para ver a lista organizada (clique numa linha para escrever o comando).

**Cachorro**
| Comando | O que faz |
|---|---|
| `/horror turn cachorro` | Vira um cachorro selvagem novo (ele pode te domesticar com ossos) |
| `/horror turn cachorro <nome>` | Entra num cachorro que já existe |
| `/horror sair` | Sai do cachorro (continua invisível) |
| `/horror sentar` | Senta/levanta (ou aperte **Shift**) |
| `/horror ver` | Mostra/esconde o cachorro na SUA tela (para se ver no F5) |
| `/horror rename <atual> to <novo>` | Renomeia um cachorro |

**Monstro**
| Comando | O que faz |
|---|---|
| `/horror monsterform [nome]` | Liga/desliga a forma de monstro |
| `/horror rugir` | Rugido alto + escuridão em quem está perto |
| `/horror perseguidor <jogador>` | O monstro aparece parado ao longe encarando ele e some quando ele olha |

**Jogadores**
| Comando | O que faz |
|---|---|
| `/horror efeito <jogador> <efeito> [segundos] [nível]` | Dá um efeito (0 segundos = infinito) |
| `/horror limpar <jogador>` | Tira todos os efeitos |
| `/horror susto <jogador> <tipo>` | caverna, passos, rosnado, raio, jumpscare, porta, vidro, grito |
| `/horror titulo <jogador> <texto>` | Texto grande só na tela dele |
| `/horror sussurro <jogador> <texto>` | O cachorro manda mensagem que só ele vê |
| `/horror olhar <jogador>` | Vira a cabeça dele para o seu cachorro/monstro |
| `/horror tp <jogador>` / `/horror trazer <jogador>` | Ir até ele / trazer ele para a sua frente |

**Mundo**
| Comando | O que faz |
|---|---|
| `/horror clima <sol\|chuva\|tempestade>` | Muda o clima |
| `/horror hora <manha\|meiodia\|pordosol\|noite\|meianoite>` | Muda a hora |
| `/horror congelar` | Congela/descongela a hora |

**Outros**
| Comando | O que faz |
|---|---|
| `/horror vanish` | Some/aparece com mensagem falsa de "saiu/entrou no jogo" |
| `/horror painel` | Coloca o botão do painel no último slot da hotbar |

## Painel
Use `/horror painel`. O botão vai sempre para o **último slot da hotbar**:
segure e clique com o botão esquerdo, ou aperte **E** e clique nele.
O botão não cai no chão e volta se você morrer.

- **Início**: status, categorias e atalhos rápidos.
- **Cachorro & Monstro**: virar/entrar/sair, sentar, me ver (F5), renomear, monsterform, rugir.
- **Jogadores** → escolha alguém: efeitos (duração e nível), tirar efeitos, TP, trazer,
  olhar fixo, jumpscare, perseguidor, mensagem na tela, sussurro e **Todos os sustos**.
- **Mundo**: clima, hora, congelar a hora.

## Como funciona
- Quando você vira cachorro, aparece "VocêNick saiu do jogo" e você some do TAB,
  do /list e da contagem de jogadores no menu de servidores.
- Tudo que você digita no chat aparece como `<NomeDoCachorro> mensagem` e num balão
  em cima do cachorro. Se você estiver invisível FORA do cachorro, seu chat é
  bloqueado para não te entregar.
- Ele pode clicar no cachorro para mandar sentar/levantar, dar comida e colocar coleira
  (a coleira te puxa de verdade).
- Quando ele domestica qualquer cachorro, abre uma tela para digitar o nome.
  Se ele fechar sem digitar, o cachorro vira **WolfWN** (ou WolfWN2, WolfWN3...),
  então você pode usar `/horror turn cachorro WolfWN`. Dá para mudar em `nome-padrao`.
- Para "aparecer" no final: `/horror sair` e depois `/horror vanish`
  (sai a mensagem "VocêNick entrou no jogo" e você aparece do lado dele).

## Monsterform
- Criatura branca de ~3 blocos com vértebras, costelas, joelhos, patas com garras,
  dentes, gengiva vermelha e **olhos vermelhos que brilham no escuro**.
- **Sangue**: manchas no peito, barriga, perna e pescoço, fios de sangue pingando
  da boca, gotas caindo, rastro no chão quando anda e respingo quando morde.
- Respira, entorta a cabeça quando parado, faz barulho de osso ao andar e ofega.
- **Superforte**: mata um jogador em **3 mordidas** (ignora armadura) e empurra longe.
- **Velocidade II** e **Pressa III** (minera muito rápido). Pode quebrar blocos.
- Se alguém morrer para você, a mensagem cita o nome do cachorro, nunca o seu nick.
- Tudo isso dá para ajustar no `config.yml` (hits para matar, velocidade, sangue...).
- Dica: troque de slot antes de quebrar blocos (com o item do painel na mão o clique abre o menu).

## Efeitos disponíveis
cegueira, escuridao, nausea, lentidao, cansaco, fraqueza, fome, veneno, wither,
levitacao, brilho, pulo, velocidade, visao

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
