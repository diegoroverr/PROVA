// --- 1. O CONTEXTO (A USINA) ---

/**
 * Decisão: Esta é a classe 'Contexto' do padrão State.
 * 1. Ela armazena o estado atual (uma referência à interface 'EstadoUsina').
 * 2. Ela fornece um método 'setEstado' para as classes de estado
 * usarem para realizar a transição.
 * 3. Ela *delega* todas as ações (eventos) para o estado atual.
 */
class UsinaNuclear {
    private EstadoUsina estadoAtual;
    private long tempoEntradaNoEstado; // Para regras complexas (ex: > 30s)

    public UsinaNuclear() {
        // Estado inicial
        this.estadoAtual = new EstadoDesligada();
        System.out.println("[SISTEMA] Usina inicializada. Estado: " + estadoAtual.getNome());
    }

    // O Contexto permite que o Estado mude o próprio estado do Contexto.
    public void setEstado(EstadoUsina novoEstado) {
        this.estadoAtual = novoEstado;
        this.tempoEntradaNoEstado = System.currentTimeMillis();
        System.out.println("\n[SISTEMA] Transição de estado para: " + novoEstado.getNome());
    }

    // Métodos que delegam o comportamento para o estado atual
    
    public void ligar() {
        this.estadoAtual.ligar(this);
    }
    
    public void desligar() {
        this.estadoAtual.desligar(this);
    }

    public void reportarStatus(int temperatura, boolean sistemaResfriamentoFalhou) {
        long tempoNesteEstado = (System.currentTimeMillis() - this.tempoEntradaNoEstado) / 1000;
        this.estadoAtual.checarSensores(this, temperatura, sistemaResfriamentoFalhou, tempoNesteEstado);
    }
    
    public void iniciarManutencao() {
        this.estadoAtual.iniciarManutencao(this);
    }
    
    public void concluirManutencao() {
        this.estadoAtual.concluirManutencao(this);
    }
}


// --- 2. A INTERFACE DO ESTADO ---

/**
 * Decisão: A interface 'State'.
 * Define os "eventos" ou "ações" que podem ocorrer na usina.
 * Cada estado concreto deverá implementar essas ações.
 */
interface EstadoUsina {
    String getNome();
    void ligar(UsinaNuclear usina);
    void desligar(UsinaNuclear usina);
    void iniciarManutencao(UsinaNuclear usina);
    void concluirManutencao(UsinaNuclear usina);
    void checarSensores(UsinaNuclear usina, int temp, boolean coolingFalhou, long tempoNoEstado);
}


// --- 3. IMPLEMENTAÇÃO BASE (OPCIONAL, MAS RECOMENDADO) ---

/**
 * Decisão: Uma classe abstrata para implementar comportamento padrão.
 * A maioria das ações é inválida na maioria dos estados.
 * Isso evita que todas as classes concretas tenham que implementar
 * métodos que não fazem nada (ex: "ligar" quando já está ligada).
 */
abstract class EstadoBase implements EstadoUsina {
    // Por padrão, a maioria das ações não faz nada ou é proibida
    public void ligar(UsinaNuclear usina) { 
        System.out.println("[" + getNome() + "] Ação 'ligar' inválida neste estado.");
    }
    public void desligar(UsinaNuclear usina) {
        System.out.println("[" + getNome() + "] Ação 'desligar' inválida neste estado.");
    }
    public void iniciarManutencao(UsinaNuclear usina) {
        System.out.println("[" + getNome() + "] Ação 'iniciarManutencao' inválida neste estado.");
    }
    public void concluirManutencao(UsinaNuclear usina) {
        System.out.println("[" + getNome() + "] Ação 'concluirManutencao' inválida neste estado.");
    }
    public void checarSensores(UsinaNuclear usina, int temp, boolean coolingFalhou, long tempoNoEstado) {
        // A checagem de sensores é o "evento" principal
    }
}


// --- 4. OS ESTADOS CONCRETOS ---

class EstadoDesligada extends EstadoBase {
    public String getNome() { return "DESLIGADA"; }

    @Override
    public void ligar(UsinaNuclear usina) {
        usina.setEstado(new EstadoOperacaoNormal());
    }
}

class EstadoOperacaoNormal extends EstadoBase {
    public String getNome() { return "OPERACAO_NORMAL"; }

    @Override
    public void checarSensores(UsinaNuclear usina, int temp, boolean coolingFalhou, long tempoNoEstado) {
        System.out.println("[" + getNome() + "] Sensores OK. Temp: " + temp + "°C");
        
        // Regra: OPERACAO_NORMAL → ALERTA_AMARELO
        if (temp > 300) {
            usina.setEstado(new EstadoAlertaAmarelo());
        }
    }
    
    @Override
    public void iniciarManutencao(UsinaNuclear usina) {
        usina.setEstado(new EstadoManutencao());
    }
    
    @Override
    public void desligar(UsinaNuclear usina) {
        usina.setEstado(new EstadoDesligada());
    }
}

class EstadoAlertaAmarelo extends EstadoBase {
    public String getNome() { return "ALERTA_AMARELO"; }

    @Override
    public void checarSensores(UsinaNuclear usina, int temp, boolean coolingFalhou, long tempoNoEstado) {
        System.out.println("[" + getNome() + "] ALERTA! Temp: " + temp + "°C (Tempo no estado: " + tempoNoEstado + "s)");

        // Regra: ALERTA_AMARELO → ALERTA_VERMELHO
        // (Simplificamos a regra dos 30s para > 400°C E estar no estado por > 5s)
        if (temp > 400 && tempoNoEstado > 5) {
            usina.setEstado(new EstadoAlertaVermelho());
        } 
        // Transição bidirecional (retorno ao normal)
        else if (temp <= 300) {
            usina.setEstado(new EstadoOperacaoNormal());
        }
    }
    
    @Override
    public void iniciarManutencao(UsinaNuclear usina) {
        usina.setEstado(new EstadoManutencao());
    }
}

class EstadoAlertaVermelho extends EstadoBase {
    public String getNome() { return "ALERTA_VERMELHO"; }

    @Override
    public void checarSensores(UsinaNuclear usina, int temp, boolean coolingFalhou, long tempoNoEstado) {
        System.out.println("[" + getNome() + "] PERIGO! Temp: " + temp + "°C. Resfriamento OK: " + !coolingFalhou);
        
        // Regra: ALERTA_VERMELHO → EMERGENCIA
        if (coolingFalhou) {
            usina.setEstado(new EstadoEmergencia());
        }
        // Transição bidirecional (retorno ao amarelo)
        else if (temp <= 400) {
            usina.setEstado(new EstadoAlertaAmarelo());
        }
    }
    
    // Regra: Não pode entrar em manutenção em alerta vermelho
    @Override
    public void iniciarManutencao(UsinaNuclear usina) {
        System.out.println("[" + getNome() + "] IMPOSSÍVEL iniciar manutenção em ALERTA VERMELHO.");
    }
}

class EstadoEmergencia extends EstadoBase {
    public String getNome() { return "EMERGENCIA"; }
    
    @Override
    public void checarSensores(UsinaNuclear usina, int temp, boolean coolingFalhou, long tempoNoEstado) {
        System.out.println("[" + getNome() + "] EVACUAR! SISTEMA CRÍTICO!");
    }
    
    // Regra: Não pode fazer NADA em emergência
    @Override
    public void ligar(UsinaNuclear usina) { }
    @Override
    public void desligar(UsinaNuclear usina) { }
    @Override
    public void iniciarManutencao(UsinaNuclear usina) { }
}

class EstadoManutencao extends EstadoBase {
    public String getNome() { return "MANUTENCAO"; }
    
    @Override
    public void checarSensores(UsinaNuclear usina, int temp, boolean coolingFalhou, long tempoNoEstado) {
        // Requisito: "sobreescreva temporariamente os estados normais"
        // O estado de manutenção ignora os alertas de temperatura.
        System.out.println("[" + getNome() + "] Sistemas em teste. Leituras de temp (" + temp + "°C) ignoradas.");
    }
    
    @Override
    public void concluirManutencao(UsinaNuclear usina) {
        usina.setEstado(new EstadoDesligada());
        System.out.println("[" + getNome() + "] Manutenção concluída. Usina desligada e pronta para operar.");
    }
}


// --- 5. O CLIENTE (DEMONSTRAÇÃO) ---

public class DemoUsinaNuclear {
    public static void main(String[] args) throws InterruptedException {
        UsinaNuclear usina = new UsinaNuclear();
        
        // Simulação de eventos
        usina.ligar(); // DESLIGADA -> OPERACAO_NORMAL
        
        usina.reportarStatus(280, false); // Normal
        usina.reportarStatus(310, false); // NORMAL -> ALERTA_AMARELO
        
        usina.reportarStatus(350, false); // Continua Amarelo
        usina.reportarStatus(290, false); // AMARELO -> NORMAL (Bidirecional)
        
        usina.reportarStatus(310, false); // NORMAL -> ALERTA_AMARELO
        usina.reportarStatus(410, false); // Temp > 400, mas tempo < 5s (ainda amarelo)

        System.out.println("[SISTEMA] Aguardando 6 segundos para simular a regra de tempo (> 5s)...");
        Thread.sleep(6000); 
        
        usina.reportarStatus(410, false); // AMARELO -> ALERTA_VERMELHO (Agora temp > 400 E tempo > 5s)
        
        // Tenta entrar em manutenção (Inválido)
        usina.iniciarManutencao(); 
        
        // Requisito: EMERGENCIA só após VERMELHO
        usina.reportarStatus(450, true); // VERMELHO -> EMERGENCIA (Cooling falhou)
        
        // Tenta checar status (está em emergência)
        usina.reportarStatus(500, true);
        
        // --- Simulação de Manutenção ---
        System.out.println("\n--- SIMULANDO NOVO CENÁRIO (MANUTENÇÃO) ---");
        UsinaNuclear usina2 = new UsinaNuclear();
        usina2.ligar();
        usina2.reportarStatus(250, false);
        usina2.iniciarManutencao(); // NORMAL -> MANUTENCAO
        
        // Requisito: "sobreescrever"
        usina2.reportarStatus(500, true); // Temperatura e cooling são ignorados
        usina2.reportarStatus(600, true); // Ignorado
        
        usina2.concluirManutencao(); // MANUTENCAO -> DESLIGADA
        usina2.ligar();
    }
}
