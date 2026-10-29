# Trabalho 1 — Exclusão mútua distribuída entre processos pares

**Ricart e Agrawala com relógio de Lamport, assinatura digital e notificação de eventos (publisher/subscriber)**

Sistemas Distribuídos · Engenharia de Computação · IFC Câmpus São Bento do Sul · Prof. Lucas Dominguez Cordeiro

| | |
|---|---|
| **Integrantes** | Isabel Schifler e Suzana Silveira |
| **Linguagem / middleware** | Java 11 ou superior, com **Java RMI** |
| **Código base** | `T1_P2P_codigo_base_atualizado2026` (versão Java RMI) |

---

## 1. O que o programa faz

Três processos pares (**PeerA**, **PeerB** e **PeerC**) disputam dois recursos compartilhados (**R1** e **R2**) **sem nenhum servidor central**. Cada processo é, ao mesmo tempo, quem pede, quem concede e quem notifica.

Para entrar na seção crítica (SC) de um recurso, o peer pede permissão aos outros dois e só entra quando recebe o OK dos dois. Toda mensagem do protocolo vai assinada digitalmente (SHA256withRSA).

---

## 2. Estrutura do projeto

```
README.md
java/
└── peer/
    ├── InterfacePeer.java   interface remota única, igual para os três peers
    ├── Peer.java            main: serviço de nomes, troca de chaves e menu
    └── PeerImpl.java        algoritmo, filas, chaves, assinaturas e log
```

---

## 3. Como compilar e executar

Requer JDK 11 ou superior (confira com `javac -version`).

### 3.1 Compilar

Sempre a partir da pasta `java/`.

**Windows (cmd ou PowerShell):**

```
cd java
javac -encoding UTF-8 peer\InterfacePeer.java peer\PeerImpl.java peer\Peer.java
```

**Linux / macOS:**

```
cd java
javac -encoding UTF-8 peer/*.java
```

### 3.2 Executar

Abra **três terminais separados, todos abertos ao mesmo tempo**, cada um na pasta `java/`. Suba **um por vez**, nesta ordem, e deixe cada um aberto:

```
java peer.Peer PeerA      (terminal 1)
java peer.Peer PeerB      (terminal 2)
java peer.Peer PeerC      (terminal 3)
```

O primeiro peer a subir cria o serviço de nomes (porta 1099) e mostra `servico de nomes CRIADO`. Os outros detectam que ele já existe e mostram `servico de nomes ja existia`. O menu só abre depois que o peer recebeu a chave pública dos outros dois.

### 3.3 Encerrar

Use a opção **0** em cada peer e feche **por último o peer que criou o serviço de nomes** (o que mostrou `CRIADO`), porque o serviço vive dentro desse processo e some junto com ele.

---

## 4. Menu

| Opção | Ação |
|---|---|
| 1 | Pedir R1 |
| 2 | Pedir R2 |
| 3 | Liberar R1 |
| 4 | Liberar R2 |
| 5 | Mostrar o estado de R1 e R2 (LIBERADO/DESEJADO/OCUPADO), o relógio de Lamport e o conteúdo das duas filas |
| 6 | Mostrar o log de mensagens enviadas e recebidas, com o resultado da verificação da assinatura (válida/inválida) |
| 7 | Enviar um pedido **adulterado** (demonstra assinatura inválida sendo rejeitada) |
| 8 | Enviar "olá" aos outros peers (teste de comunicação) |
| 9 | Listar os nomes registrados no serviço de nomes |
| 0 | Sair |

---

## 5. Política contra impasse entre recursos: **um recurso por vez**

Um peer só pode pedir R1 se R2 estiver `LIBERADO` para ele, e vice-versa. Se tentar pedir o segundo recurso enquanto estiver usando ou esperando o primeiro, o menu recusa e avisa que é preciso liberar o primeiro antes. A verificação está no método `pedir`, dentro da trava.

**Justificativa.** O impasse entre recursos (A detém R1 e pede R2 enquanto B detém R2 e pede R1) exige a condição de "manter e esperar": segurar um recurso enquanto espera por outro. Com a regra de um recurso por vez, nenhum peer espera por um recurso segurando outro, então o ciclo de espera não se forma.

Os dois recursos continuam **independentes**: peers diferentes podem usar R1 e R2 ao mesmo tempo.

**Limitação.** Um mesmo peer nunca usa R1 e R2 juntos. A alternativa seria a ordem fixa de aquisição R1→R2, que permite isso, mas exige mais controle no código.

Dentro de um mesmo recurso, o impasse e a inanição são evitados pela própria ordem total `(timestamp, nome)` do algoritmo.
