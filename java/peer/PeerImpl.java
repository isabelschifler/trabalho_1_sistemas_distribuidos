package peer;

import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Classe servente do peer: guarda o estado local e implementa o algoritmo de
 * Ricart e Agrawala (exclusão mútua distribuída) para dois recursos, com
 * relógio lógico de Lamport e mensagens assinadas digitalmente.
 *
 * REGRA DE OURO DA CONCORRÊNCIA: o estado compartilhado só é mexido dentro de
 * synchronized(trava), e NENHUMA chamada remota é feita com a trava segurada
 * (senão dois peers podem ficar esperando um ao outro para sempre). Por isso
 * os envios saem sempre numa thread separada (método enviarAsync).
 */
public class PeerImpl extends UnicastRemoteObject implements InterfacePeer {

    public static final List<String> RECURSOS = List.of("R1", "R2");

    public enum Estado { LIBERADO, DESEJADO, OCUPADO }

    private final String nome;

    /** Referências de objeto remoto dos OUTROS peers (nome -> referência). */
    private final Map<String, InterfacePeer> outrosPeers = new ConcurrentHashMap<>();

    // ---- Assinatura digital ----
    private final PrivateKey chavePrivada;
    private final PublicKey chavePublica;
    private final Map<String, PublicKey> chavesDosOutros = new ConcurrentHashMap<>();

    // ---- Ricart e Agrawala (tudo protegido por "trava") ----
    private final Object trava = new Object();
    private long relogio = 0; // relógio lógico de Lamport
    private final Map<String, Estado> estado = new HashMap<>();
    private final Map<String, Long> tsPedido = new HashMap<>();
    /** Fila de pedidos adiados por recurso = "registros de interesse" (subscribers). */
    private final Map<String, LinkedList<String>> fila = new HashMap<>();
    /** Quem já deu permissão (OK) para o meu pedido atual, por recurso. */
    private final Map<String, Set<String>> permissoes = new HashMap<>();

    /** Threads para os envios assíncronos (daemon: não impedem o programa de fechar). */
    private final ExecutorService envios = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    public PeerImpl(String nome) throws RemoteException {
        super(); // exporta o objeto
        this.nome = nome;
        try {
            KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
            gerador.initialize(2048);
            KeyPair par = gerador.generateKeyPair();
            this.chavePrivada = par.getPrivate();
            this.chavePublica = par.getPublic();
        } catch (GeneralSecurityException e) {
            throw new RemoteException("Falha ao gerar o par de chaves", e);
        }
        for (String r : RECURSOS) {
            estado.put(r, Estado.LIBERADO);
            fila.put(r, new LinkedList<>());
            permissoes.put(r, new HashSet<>());
        }
    }

    public void adicionarPeer(String nomePeer, InterfacePeer referencia) {
        outrosPeers.put(nomePeer, referencia);
    }

    public Map<String, InterfacePeer> getOutrosPeers() {
        return outrosPeers;
    }

    // ------------------------------------------------------------------
    // Chaves e assinaturas
    // ------------------------------------------------------------------

    /** Envia a minha chave pública para todos os outros peers. */
    public void distribuirChavePublica() {
        for (Map.Entry<String, InterfacePeer> e : outrosPeers.entrySet()) {
            try {
                e.getValue().receberChavePublica(nome, chavePublica);
            } catch (RemoteException ex) {
                System.out.println("[" + nome + "] falha ao enviar chave a " + e.getKey() + ": " + ex.getMessage());
            }
        }
    }

    /** Bloqueia até ter a chave pública de todos os outros peers. */
    public void aguardarChaves() throws InterruptedException {
        boolean avisou = false;
        while (chavesDosOutros.size() < outrosPeers.size()) {
            if (!avisou) {
                System.out.println("[" + nome + "] aguardando chaves públicas dos outros peers...");
                avisou = true;
            }
            Thread.sleep(200);
        }
        System.out.println("[" + nome + "] chaves públicas de todos os peers recebidas.");
    }

    @Override
    public void receberChavePublica(String remetente, PublicKey chave) throws RemoteException {
        chavesDosOutros.put(remetente, chave);
        System.out.println("\n[" + nome + "] chave pública de " + remetente + " recebida.");
    }

    private byte[] assinar(String conteudo) {
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initSign(chavePrivada);
            s.update(conteudo.getBytes(StandardCharsets.UTF_8));
            return s.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao assinar", e);
        }
    }

    private boolean assinaturaValida(String remetente, String conteudo, byte[] assinatura) {
        PublicKey chave = chavesDosOutros.get(remetente);
        if (chave == null || assinatura == null) {
            return false;
        }
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(chave);
            s.update(conteudo.getBytes(StandardCharsets.UTF_8));
            return s.verify(assinatura);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Métodos remotos (chamados POR OUTROS peers)
    // ------------------------------------------------------------------

    @Override
    public String getNome() throws RemoteException {
        return nome;
    }

    @Override
    public void ola(String remetente) throws RemoteException {
        System.out.println("\n[" + nome + "] recebeu olá de " + remetente);
    }

    /** Chegou um PEDIDO de outro peer: responde na hora ou adia (entra na fila). */
    @Override
    public void solicitarRecurso(String remetente, String recurso, long timestamp, byte[] assinatura)
            throws RemoteException {
        String conteudo = "PEDIDO|" + remetente + "|" + recurso + "|" + timestamp;
        if (!RECURSOS.contains(recurso) || !assinaturaValida(remetente, conteudo, assinatura)) {
            System.out.println("\n[" + nome + "] PEDIDO inválido (assinatura ou recurso) de " + remetente + " — ignorado.");
            return;
        }

        boolean adiar;
        synchronized (trava) {
            relogio = Math.max(relogio, timestamp) + 1; // regra de Lamport ao receber
            Estado meu = estado.get(recurso);
            boolean souMaisAntigo = tsPedido.getOrDefault(recurso, Long.MAX_VALUE) < timestamp
                    || (tsPedido.getOrDefault(recurso, Long.MAX_VALUE) == timestamp
                        && nome.compareTo(remetente) < 0); // empate: desempata pelo nome
            adiar = (meu == Estado.OCUPADO) || (meu == Estado.DESEJADO && souMaisAntigo);
            if (adiar) {
                fila.get(recurso).add(remetente);
            }
        }

        if (adiar) {
            System.out.println("\n[" + nome + "] pedido de " + remetente + " por " + recurso
                    + " (ts=" + timestamp + ") ADIADO — entrou na fila.");
        } else {
            System.out.println("\n[" + nome + "] pedido de " + remetente + " por " + recurso
                    + " (ts=" + timestamp + ") — dando permissão.");
            enviarNotificacaoAsync(remetente, recurso);
        }
    }

    /** Chegou uma permissão (OK / "recurso liberado") de outro peer. */
    @Override
    public void notificar(String remetente, String recurso, long timestamp, byte[] assinatura)
            throws RemoteException {
        String conteudo = "LIBERADO|" + remetente + "|" + recurso + "|" + timestamp;
        if (!RECURSOS.contains(recurso) || !assinaturaValida(remetente, conteudo, assinatura)) {
            System.out.println("\n[" + nome + "] NOTIFICAÇÃO inválida (assinatura ou recurso) de " + remetente + " — ignorada.");
            return;
        }

        boolean entrou = false;
        synchronized (trava) {
            relogio = Math.max(relogio, timestamp) + 1;
            if (estado.get(recurso) == Estado.DESEJADO) {
                permissoes.get(recurso).add(remetente);
                if (permissoes.get(recurso).size() == outrosPeers.size()) {
                    estado.put(recurso, Estado.OCUPADO);
                    entrou = true;
                }
            }
        }
        System.out.println("\n[" + nome + "] permissão de " + remetente + " para " + recurso + " recebida.");
        if (entrou) {
            System.out.println("[" + nome + "] *** ENTROU na seção crítica de " + recurso + " ***");
        }
    }

    // ------------------------------------------------------------------
    // Métodos locais (chamados pelo menu deste próprio peer)
    // ------------------------------------------------------------------

    /** Pede acesso a um recurso: manda o PEDIDO assinado a todos os outros peers. */
    public void pedir(String recurso) {
        long ts;
        synchronized (trava) {
            if (estado.get(recurso) != Estado.LIBERADO) {
                System.out.println("[" + nome + "] você já pediu ou está usando " + recurso + ".");
                return;
            }
            estado.put(recurso, Estado.DESEJADO);
            permissoes.get(recurso).clear();
            relogio++;
            ts = relogio;
            tsPedido.put(recurso, ts);
        }
        System.out.println("[" + nome + "] pedindo " + recurso + " (ts=" + ts + ")...");
        byte[] assinatura = assinar("PEDIDO|" + nome + "|" + recurso + "|" + ts);
        for (Map.Entry<String, InterfacePeer> e : outrosPeers.entrySet()) {
            enviarAsync(e.getKey(), () -> e.getValue().solicitarRecurso(nome, recurso, ts, assinatura));
        }
    }

    /** Libera um recurso e avisa (assíncrono) quem estava na fila de interesse. */
    public void liberar(String recurso) {
        List<String> interessados;
        synchronized (trava) {
            if (estado.get(recurso) != Estado.OCUPADO) {
                System.out.println("[" + nome + "] você não está usando " + recurso + ".");
                return;
            }
            estado.put(recurso, Estado.LIBERADO);
            tsPedido.remove(recurso);
            permissoes.get(recurso).clear();
            interessados = new ArrayList<>(fila.get(recurso));
            fila.get(recurso).clear();
        }
        System.out.println("[" + nome + "] " + recurso + " LIBERADO. Avisando na fila: " + interessados);
        for (String destino : interessados) {
            enviarNotificacaoAsync(destino, recurso);
        }
    }

    /** Monta, assina e envia (em outra thread) a notificação "recurso liberado" a um peer. */
    private void enviarNotificacaoAsync(String destino, String recurso) {
        long ts;
        synchronized (trava) {
            relogio++;
            ts = relogio;
        }
        byte[] assinatura = assinar("LIBERADO|" + nome + "|" + recurso + "|" + ts);
        InterfacePeer ref = outrosPeers.get(destino);
        enviarAsync(destino, () -> ref.notificar(nome, recurso, ts, assinatura));
    }

    /** Uma chamada remota que pode lançar RemoteException. */
    private interface ChamadaRemota {
        void executar() throws RemoteException;
    }

    /** Executa a chamada remota numa thread separada, sem bloquear quem chamou. */
    private void enviarAsync(String destino, ChamadaRemota chamada) {
        envios.submit(() -> {
            try {
                chamada.executar();
            } catch (RemoteException ex) {
                System.out.println("\n[" + nome + "] falha ao falar com " + destino + ": " + ex.getMessage());
            }
        });
    }

    /** Mostra relógio, estado de cada recurso e as filas. */
    public void mostrarEstado() {
        synchronized (trava) {
            System.out.println("Relógio lógico: " + relogio);
            for (String r : RECURSOS) {
                System.out.println(r + ": estado=" + estado.get(r)
                        + (tsPedido.containsKey(r) ? ", ts do pedido=" + tsPedido.get(r) : "")
                        + ", permissões=" + permissoes.get(r)
                        + ", fila de adiados=" + fila.get(r));
            }
        }
    }

    public void enviarOlaParaTodos() {
        for (Map.Entry<String, InterfacePeer> e : outrosPeers.entrySet()) {
            try {
                e.getValue().ola(nome);
                System.out.println("[" + nome + "] olá entregue a " + e.getKey());
            } catch (RemoteException ex) {
                System.out.println("[" + nome + "] falha ao falar com " + e.getKey() + ": " + ex.getMessage());
            }
        }
    }
}