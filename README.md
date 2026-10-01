# Trabalho 1 — código base (esqueleto P2P)

Sistemas Distribuídos · Engenharia de Computação · IFC São Bento do Sul

Esqueleto mínimo de uma aplicação de **processos pares** com três peers (`PeerA`, `PeerB`,
`PeerC`) na mesma máquina, em duas versões equivalentes — escolha **uma**:

- `java/peer/` — Java RMI (requer JDK 11+)
- `python/` — Pyro5 (requer Python 3.8+ e `pip install Pyro5`)

## O que o esqueleto JÁ faz

- Um **único programa** executado três vezes, cada vez com um nome. Não existe "servidor" e
  "cliente": todo peer expõe a **mesma interface remota** e todo peer chama os outros.
- **Serviço de nomes único**: o primeiro peer a subir cria o serviço de nomes; os demais
  percebem que ele já existe e apenas obtêm sua referência (inclusive se dois peers forem
  iniciados ao mesmo tempo).
- Cada peer se registra com o seu nome, **espera os outros dois entrarem** e guarda a
  referência (Java) / URI (Python) de cada um.
- Comunicação **unicast** de teste (`ola`) e um método `notificar` vazio para receber
  notificações de eventos.
- Menu mínimo (`1` olá, `2` listar nomes, `0` sair) e o PID de cada processo na tela.

## O que o esqueleto NÃO faz (é o Trabalho 1)

Procure por `TODO (T1)` no código:

- algoritmo de Ricart e Agrawala com relógio lógico de Lamport, para **dois recursos**;
- uma fila de pedidos adiados (registros de interesse) **por recurso**;
- geração do par de chaves, troca das chaves públicas e **assinatura digital** de toda mensagem;
- notificação **assíncrona** do evento "recurso liberado" (publisher → subscriber);
- menu completo (pedir/liberar R1 e R2, mostrar estado e filas).

## Java RMI

A partir da pasta `java/`:

```
javac -encoding UTF-8 peer/*.java
```

Em três terminais:

```
java peer.Peer PeerA
java peer.Peer PeerB
java peer.Peer PeerC
```

## Python com Pyro5

A partir da pasta `python/` — **não** é preciso rodar `pyro5-ns` à parte, o primeiro peer cria
o serviço de nomes:

```
python3 peer.py PeerA
python3 peer.py PeerB
python3 peer.py PeerC
```

## Roteiro rápido de teste

1. Suba os três peers. O primeiro mostra `serviço de nomes CRIADO`; os outros, `já existia`.
2. Enquanto faltar alguém, cada peer mostra `aguardando PeerX entrar...`.
3. Em qualquer peer, escolha `1`: os outros dois mostram `recebeu olá de ...`.
4. Escolha `2` para ver os nomes registrados (`list`).

## Cuidados que valem para o trabalho

- **Concorrência:** RMI e Pyro5 atendem cada chamada remota numa thread própria, ao mesmo tempo
  que o menu roda na thread principal. Proteja filas, relógio e estado dos recursos
  (`synchronized` / `threading.Lock`).
- **Pyro5 e threads:** um `Proxy` pertence à thread que o criou. Por isso o esqueleto guarda a
  URI e abre um `Proxy` novo a cada chamada. Se preferir guardar o proxy, chame
  `_pyroClaimOwnership()` antes de usá-lo em outra thread.
- **Quem criou o serviço de nomes:** ele vive dentro desse processo. Encerre esse peer por
  último.
