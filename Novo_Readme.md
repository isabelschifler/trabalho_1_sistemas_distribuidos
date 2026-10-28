Trabalho 1 — Exclusão mútua distribuída entre processos pares

Ricart e Agrawala com relógio de Lamport, assinatura digital e notificação de eventos (publisher/subscriber)

Sistemas Distribuídos · Engenharia de Computação · IFC São Bento do Sul

Integrante(s):Isabel Schifler e Suzana Silveira 
Linguagem / middleware: Java 11+ com Java RMI

O que o programa faz
Três processos pares (PeerA, PeerB e PeerC) disputam dois recursos compartilhados (R1 e R2) sem nenhum servidor central. Para entrar na seção crítica de um recurso, o peer pede permissão aos outros dois e só entra quando recebe o OK dos dois. Toda mensagem do protocolo vai assinada digitalmente.

Estrutura do projeto
README.md
java/
└── peer/
    ├── InterfacePeer.java   interface remota única, igual para os três peers
    ├── Peer.java            main: serviço de nomes, troca de chaves e menu
    └── PeerImpl.java        algoritmo, filas, chaves, assinaturas e log
Como compilar e executar

Requer JDK 11 ou superior (javac -version).

1. Compilar, a partir da pasta java/ (no Windows use a barra invertida):

cd java
javac -encoding UTF-8 peer\*.java

2. Executar, em três terminais, todos na pasta java/:

java peer.Peer PeerA
java peer.Peer PeerB
java peer.Peer PeerC

O primeiro peer a subir cria o serviço de nomes (porta 1099) e mostra servico de nomes CRIADO. Os outros detectam que ele já existe e mostram servico de nomes ja existia. O menu só abre depois que o peer recebeu a chave pública dos outros dois.

3. Encerrar: use a opção 0 em cada peer e feche por último o peer que criou o serviço de nomes, porque o serviço vive dentro desse processo.

Possíveis problemas

Could not find or load main class: o terminal não está na pasta java/.
Porta 1099 em uso: sobrou um peer antigo aberto; feche todos e tente de novo.
Caracteres estranhos no cmd do Windows: rode chcp 65001 antes do java.
Menu
Opção	Ação
1	Pedir R1
2	Pedir R2
3	Liberar R1
4	Liberar R2
5	Mostrar estado de R1 e R2, relógio de Lamport e as duas filas
6	Mostrar o log de mensagens, com o resultado da verificação da assinatura
7	Enviar um pedido adulterado (demonstra assinatura inválida sendo rejeitada)
8	Enviar "olá" aos outros peers (teste de comunicação)
9	Listar os nomes registrados no serviço de nomes
0	Sair
Política contra impasse entre recursos: um recurso por vez

Um peer só pode pedir R1 se R2 estiver LIBERADO para ele, e vice-versa. Se tentar pedir o segundo recurso enquanto estiver usando ou esperando o primeiro, o menu recusa e avisa que é preciso liberar o primeiro antes.

Justificativa. O impasse entre recursos (A detém R1 e pede R2 enquanto B detém R2 e pede R1) exige a condição de "manter e esperar": segurar um recurso enquanto espera por outro. Com a regra de um recurso por vez, nenhum peer espera por um recurso segurando outro, então o ciclo de espera não se forma. Os dois recursos continuam independentes: peers diferentes podem usar R1 e R2 ao mesmo tempo.

Limitação. Um mesmo peer nunca usa R1 e R2 juntos. A alternativa seria a ordem fixa de aquisição R1→R2, que permite isso, mas exige mais controle no código.

Dentro de um mesmo recurso, o impasse e a inanição são evitados pela própria ordem total (timestamp, id) do algoritmo.

Como o programa funciona
Algoritmo de Ricart e Agrawala
Cada peer mantém, para cada recurso, o estado LIBERADO, DESEJADO ou OCUPADO.
Ao pedir, o peer marca DESEJADO, avança o relógio e envia o pedido (timestamp, nome) a cada um dos outros dois peers.
Quem recebe um pedido adia a resposta (põe na fila do recurso) se estiver OCUPADO nesse recurso, ou DESEJADO com pedido de menor (timestamp, nome). Caso contrário, responde OK imediatamente.
O peer só entra na seção crítica depois de receber o OK de todos os outros (N − 1 = 2). Ao sair, responde a todos os pedidos adiados daquele recurso.
Relógio de Lamport
Ao enviar uma mensagem: relogio = relogio + 1.
Ao receber: relogio = max(relogio local, timestamp recebido) + 1.
Empates de timestamp são desfeitos pelo nome: PeerA < PeerB < PeerC.
Unicast

O pedido é enviado por chamadas remotas individuais a cada um dos outros peers, e não por multicast.

Chaves e assinatura digital
Ao iniciar, cada peer gera um par de chaves RSA de 2048 bits e envia a chave pública aos outros. Cada peer guarda a chave pública e a referência remota dos demais.
Toda mensagem do protocolo (pedido e resposta/notificação) é assinada com SHA256withRSA sobre o texto TIPO|remetente|recurso|timestamp.
O destinatário confere a assinatura com a chave pública do remetente. Se for inválida, a mensagem é descartada e registrada no log, sem alterar relógio, estado nem filas.
A assinatura garante autenticidade e integridade, mas não sigilo (a mensagem não é cifrada).
Duas filas e publisher/subscriber
Cada peer tem uma fila de pedidos adiados para R1 e outra para R2.
Um pedido que não pode ser atendido na hora é um registro de interesse no evento "recurso liberado", guardado na fila do recurso (papel de subscriber).
Ao liberar o recurso, o peer atua como publisher: chama o método notificar de cada peer da fila e retira o pedido da fila.
A notificação é assíncrona: cada envio sai numa thread separada (ExecutorService), então quem libera não fica bloqueado esperando o outro.
A notificação é o OK que estava adiado. Ela não significa, sozinha, que o peer pode usar o recurso: ele só entra quando tiver a permissão de todos os outros.
Concorrência

O RMI atende cada chamada remota numa thread própria, em paralelo com o menu. Por isso relógio, estados e filas são protegidos por synchronized(trava). Nenhuma chamada remota é feita com a trava segurada, para evitar que dois peers fiquem esperando um ao outro. O estado é atualizado sob a trava, a trava é solta e só então o envio é disparado em outra thread.

Roteiro de demonstração
Um peer sem concorrência. O PeerA pede R1 (opção 1), recebe os dois OKs e entra na seção crítica. Confira com a opção 5 e libere com a opção 3.
Concorrência no mesmo recurso. O PeerA entra em R1. O PeerB e o PeerC pedem R1 quase ao mesmo tempo. Os dois ficam na fila do PeerA (opção 5). Ao PeerA liberar, entra quem tem o menor (timestamp, nome); o outro entra depois que esse liberar.
R1 e R2 ao mesmo tempo. O PeerA usa R1 enquanto o PeerB usa R2.
Assinatura inválida. Em um peer, escolha a opção 7. Nos outros dois, a opção 6 mostra a linha assinatura INVALIDA, mensagem DESCARTADA.
Política contra impasse. Com um recurso em uso, tente pedir o outro (opção 1 ou 2) e veja a recusa.
Mapa dos critérios do trabalho
Critério	Onde está
Relógio de Lamport e desempate por id	PeerImpl: relogio e souMaisAntigo
Pedido, resposta e adiamento (3 estados)	PeerImpl: pedir, solicitarRecurso, notificar, liberar
Segurança e ordenação com pedidos concorrentes	Demonstração 2
Dois recursos e política contra impasse	Mapas por recurso e verificação em pedir
Unicast, chaves públicas e referências remotas	pedir, distribuirChavePublica, chavesDosOutros, outrosPeers
Assinatura em toda mensagem e rejeição	assinar, assinaturaValida, opção 7 do menu
Publisher/subscriber assíncrono	liberar, enviarNotificacaoAsync, enviarAsync
Interface mínima (seção 6)	Menu: opções 1 a 6.