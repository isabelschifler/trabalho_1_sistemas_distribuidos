# Resumão do Trabalho 1 (pra gente entender e apresentar)

## A ideia em 30 segundos

Temos 3 programas rodando juntos (PeerA, PeerB, PeerC) e 2 "coisas" que todo mundo quer usar
(R1 e R2). Cada coisa só pode ser usada por **um peer por vez**. Não tem chefe: os três se
falam e combinam sozinhos quem usa. Quando um peer pede, os outros dois precisam **dar o OK**.

O trabalho junta 4 ideias:

- **Java RMI**: um programa chama método de outro programa como se fosse local.
- **Ricart e Agrawala**: o jeito de decidir quem entra, sem chefe.
- **Relógio de Lamport**: um contador pra saber quem pediu primeiro.
- **Assinatura digital**: pra ninguém fingir ser outro peer.

---

## Palavras que vão aparecer

- **Peer**: cada um dos 3 programas. Todos são iguais (não tem servidor nem cliente).
- **Seção crítica (SC)**: quando o peer está *usando* o recurso. Só um por vez.
- **Serviço de nomes**: tipo uma lista telefônica. Cada peer se cadastra ali e os outros acham ele por lá.
- **Thread**: uma "linha de execução". O programa faz várias coisas ao mesmo tempo com threads.
- **Assíncrono**: mandar a mensagem e **não ficar esperando** a resposta.
- **Chave privada / pública**: a privada é secreta e **assina** a mensagem. A pública todo mundo tem e **confere** a assinatura.

---

## Os 3 arquivos

| Arquivo | O que é | Analogia |
|---|---|---|
| `InterfacePeer.java` | Lista do que um peer aceita receber dos outros | O cardápio |
| `PeerImpl.java` | Onde mora a lógica toda (estado, algoritmo, assinatura) | A cozinha |
| `Peer.java` | Liga tudo e mostra o menu | A porta de entrada |

### InterfacePeer: o que um peer recebe

- `ola(...)`: mensagem de teste.
- `receberChavePublica(...)`: "toma minha chave pública".
- `solicitarRecurso(...)`: "eu quero usar R1/R2" (o pedido).
- `notificar(...)`: "pode usar" (o OK) e também o aviso de "recurso liberado".

> O `notificar` faz **dois papéis**: é o OK do algoritmo e é a notificação de evento
> (quem libera é o *publisher*, quem estava esperando é o *subscriber*).

---

## O que acontece quando o programa liga (Peer.java)

1. Tenta criar a lista telefônica (porta 1099). Se já existe, só pega a que já existe. Por isso tanto faz quem sobe primeiro.
2. Cria o `PeerImpl` (aqui já nasce o par de chaves).
3. Se cadastra na lista telefônica com o próprio nome.
4. Espera os outros dois aparecerem e guarda o "contato" deles.
5. Manda a chave pública pros outros e espera receber as deles (só então o menu abre).
6. Abre o menu.

---

## O menu

| Opção | Faz o quê |
|---|---|
| 1 / 2 | Pede R1 / R2 |
| 3 / 4 | Libera R1 / R2 |
| 5 | Mostra estado, permissões, filas e relógio |
| 6 | Manda "olá" pros outros (teste) |
| 7 | Lista quem está na lista telefônica |
| 0 | Sai |

---

## Como o algoritmo funciona (a parte principal)

### Estados de cada recurso

```
LIBERADO --(eu peço)--> DESEJADO --(todos deram OK)--> OCUPADO --(eu libero)--> LIBERADO
```

Cada recurso (R1 e R2) tem seu próprio estado, fila e permissões. Então dá pra um usar R1
enquanto outro usa R2.

### Relógio de Lamport (só 2 regras)

1. Ao **enviar** mensagem: soma 1 no relógio e manda esse número junto.
2. Ao **receber** mensagem com número T: relógio = maior(meu, T) + 1.

O número do pedido é o "timestamp". Quanto menor, mais cedo pediu.

### Quando eu peço (`pedir`)

1. Só deixa se o recurso estiver LIBERADO.
2. Vira DESEJADO, soma 1 no relógio e guarda esse número como o timestamp do meu pedido.
3. Assina o pedido e manda pros outros dois (cada envio em uma thread separada).
4. O menu **não trava**: o resultado aparece depois, quando os OKs chegarem.

### Quando alguém me pede (`solicitarRecurso`)

Primeiro confere a assinatura. Depois decide:

- **Adia** (põe o cara na fila) se eu estou **usando** o recurso, **ou** se eu também estou
  querendo e meu pedido é **mais antigo** que o dele.
- **Dá OK na hora** em qualquer outro caso.

Quem é mais antigo? Menor timestamp. Se empatar, **o nome menor ganha** (PeerA > PeerB > PeerC).

### Quando recebo um OK (`notificar`)

Confere a assinatura e anota quem deu OK. Quando os **dois** deram, vira OCUPADO e aparece
`ENTROU na seção crítica`.

### Quando eu libero (`liberar`)

1. Volta pra LIBERADO.
2. Pega a fila de quem estava esperando e esvazia.
3. Manda a notificação "liberado" pra cada um da fila, **de forma assíncrona**.

---

## Assinatura digital

- Cada mensagem tem um texto tipo `PEDIDO|PeerA|R1|3` ou `LIBERADO|PeerB|R1|5`.
- Quem envia assina com a **chave privada** dele.
- Quem recebe confere com a **chave pública** do remetente.
- Se a conferência falhar, a mensagem é **ignorada** e aparece "inválido" na tela.

---

## Exemplos pra gente lembrar

**Só um quer o recurso:** PeerA pede R1 → B e C estão livres e dão OK na hora → A entra.

**Dois disputando:** A já está usando R1 e B pede R1.
- C dá OK na hora.
- A **adia** (B vai pra fila de A; dá pra ver na opção 5).
- B fica esperando.
- A libera (opção 3) → A avisa B → B entra.

**Pedidos ao mesmo tempo:** A e B pedem R1 quase juntos. Cada um compara os timestamps; quem
tem o menor (ou o nome menor, se empatar) ganha. O outro dá OK e espera. **Nunca entram os dois.**

**Recursos diferentes:** A pede R1 e B pede R2 → os dois entram, porque são disputas separadas.

---

## Por que tem `trava` e `enviarAsync` no código?

Dentro de um peer, muita coisa acontece junto: o menu e cada chamada que chega de outro peer
(uma thread pra cada).

- **`synchronized (trava)`** é um cadeado: só uma thread mexe no estado por vez. Sem isso os
  dados embaralham.
- **Nunca chamamos outro peer com o cadeado fechado.** Se dois peers segurassem o cadeado e
  esperassem um ao outro, travava pra sempre (deadlock). Por isso todo envio vai pelo
  `enviarAsync`, fora do cadeado.
- O envio assíncrono também é o que o enunciado pede: quem libera não fica parado esperando.

---

## Como rodar

Na pasta `java`:

```
javac -encoding UTF-8 peer/*.java
```

Em 3 terminais (cada um na pasta `java`):

```
java peer.Peer PeerA
java peer.Peer PeerB
java peer.Peer PeerC
```

Pra fechar: opção `0` em cada um, e o que mostrou `CRIADO` fecha **por último**.

---

## Roteiro pra demonstrar pro profe

1. Sobe os 3 e mostra o `CRIADO` / `já existia` e a troca de chaves.
2. Opção `6` pra mostrar que eles se falam.
3. A pede R1 e entra. B pede R1 e fica esperando. Mostra a fila (opção `5`).
4. A libera e o B entra (mostra a notificação assíncrona).
5. Pedidos simultâneos no mesmo recurso (mostra que só um entra).
6. R1 e R2 ao mesmo tempo em peers diferentes (mostra que são independentes).

---

## Perguntas que o profe pode fazer

- **Por que não tem servidor?** Porque é P2P: todo mundo tem a mesma interface e chama todo mundo.
- **Quem decide quem entra?** Ninguém sozinho: menor timestamp ganha, empate resolve pelo nome.
- **Por que não usar a hora do computador?** Os relógios de máquinas diferentes não são
  sincronizados direito. O de Lamport só ordena eventos, e isso basta.
- **Pra que serve a fila?** Guarda quem pediu enquanto eu usava (os "registros de interesse").
  Quando libero, aviso todos da fila.
- **Onde está o publisher/subscriber?** Quem libera é o publisher, quem estava na fila é o subscriber.
- **Como garante que a mensagem é de quem diz ser?** Assinatura com chave privada, conferida com a pública.
- **R1 e R2 se bloqueiam?** Não, cada um tem estado, fila e permissões próprios.
- **E se um peer cair?** O enunciado assume que não cai. Se o que criou o serviço de nomes sair, a lista some junto.