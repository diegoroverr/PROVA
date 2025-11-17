import java.util.HashMap;

// --- 1. O SISTEMA LEGADO (ADAPTEE) ---
// (Não podemos modificar esta classe)

/**
 * Decisão: Esta é a classe legada (Adaptee) com a interface incompatível.
 * Ela recebe um HashMap e retorna uma String de status simples.
 */
class SistemaBancarioLegado {
    
    public String processarTransacao(HashMap<String, Object> parametros) {
        System.out.println("\n--- [SISTEMA LEGADO] Recebido 'HashMap': ---");
        
        // O legado verifica seus campos obrigatórios
        if (!parametros.containsKey("AUTH_KEY") || 
            !parametros.containsKey("TRANSACTION_DATA")) {
            return "ERRO_CONFIG:AUTH_KEY_OU_DATA_FALTANDO";
        }
        
        // Simulação da lógica legada
        System.out.println(parametros);
        double valor = (Double) parametros.get("TRANSACTION_VALUE");
        
        if (valor > 1000) {
            return "RECUSADO:LIMITE_EXCEDIDO";
        }
        return "APROVADO:200_OK";
    }
}


// --- 2. INTERFACE MODERNA E DTOs (TARGET) ---
// (Esta é a interface que nosso cliente quer usar)

/**
 * Decisão: DTO (Data Transfer Object) para a Resposta moderna.
 * Oculta a complexidade da resposta legada (String).
 */
class RespostaAutorizacao {
    private boolean sucesso;
    private String mensagem;

    public RespostaAutorizacao(boolean sucesso, String mensagem) {
        this.sucesso = sucesso;
        this.mensagem = mensagem;
    }

    @Override
    public String toString() {
        return "RespostaAutorizacao [sucesso=" + sucesso + ", mensagem='" + mensagem + "']";
    }
}

/**
 * Decisão: Esta é a interface moderna (Target).
 * Oculta totalmente o HashMap e os códigos de moeda.
 */
interface ProcessadorTransacoes {
    RespostaAutorizacao autorizar(String cartao, double valor, String moeda);
}


// --- 3. O ADAPTADOR (ADAPTER) ---

/**
 * Decisão: Esta é a classe Adapter.
 * 1. Implementa a interface moderna (Target).
 * 2. "Contém" (compõe) a classe legada (Adaptee).
 * 3. Faz a "tradução" bidirecional.
 */
class AdapterBancario implements ProcessadorTransacoes {

    private SistemaBancarioLegado legado;

    // O adaptador recebe o objeto que ele precisa adaptar (Injeção de Dependência)
    public AdapterBancario(SistemaBancarioLegado legado) {
        this.legado = legado;
    }

    @Override
    public RespostaAutorizacao autorizar(String cartao, double valor, String moeda) {
        
        System.out.println("[ADAPTER] Recebida chamada moderna. Traduzindo para o legado...");

        // --- PARTE 1: Tradução do REQUEST (Moderno -> Legado) ---
        
        // 1. Criar o mapa legado
        HashMap<String, Object> parametrosLegados = new HashMap<>();
        
        // 2. Mapear os campos diretos
        parametrosLegados.put("CARD_NUMBER_STR", cartao);
        parametrosLegados.put("TRANSACTION_VALUE", valor);
        
        // 3. Lidar com a RESTRIÇÃO (Codificação de Moeda)
        parametrosLegados.put("CURRENCY_CODE_INT", converterMoedaParaCodigo(moeda));
        
        // 4. Lidar com o REQUISITO (Campo obrigatório que não existe no moderno)
        // O legado espera um "AUTH_KEY" e "TRANSACTION_DATA" que o moderno não tem.
        // O Adapter é responsável por "fabricar" esses dados.
        parametrosLegados.put("AUTH_KEY", "chave_fixa_de_integracao_xyz");
        // Agrupando dados em um sub-mapa, como é comum em sistemas legados
        HashMap<String, String> subData = new HashMap<>();
        subData.put("card", cartao);
        subData.put("currency", moeda);
        parametrosLegados.put("TRANSACTION_DATA", subData); // O legado queria um mapa aninhado

        // 5. Chamar o sistema legado
        String respostaLegada = this.legado.processarTransacao(parametrosLegados);
        
        // --- PARTE 2: Tradução da RESPOSTA (Legado -> Moderno) ---
        // (Cumprindo o requisito "bidirecional")
        
        return converterRespostaLegada(respostaLegada);
    }

    // Método auxiliar privado para a lógica de tradução
    private int converterMoedaParaCodigo(String moeda) {
        switch (moeda.toUpperCase()) {
            case "USD": return 1;
            case "EUR": return 2;
            case "BRL": return 3;
            default: return 0; // Código para "desconhecido"
        }
    }
    
    // Método auxiliar privado para a lógica de tradução "bidirecional"
    private RespostaAutorizacao converterRespostaLegada(String respostaLegada) {
        if (respostaLegada.equals("APROVADO:200_OK")) {
            return new RespostaAutorizacao(true, "Transação aprovada com sucesso.");
        } else {
            // Oculta a mensagem de erro críptica do legado
            return new RespostaAutorizacao(false, "Transação recusada pelo sistema. (Cód: " + respostaLegada + ")");
        }
    }
}


// --- 4. O CLIENTE ---

/**
 * Decisão: Classe Cliente (Demo).
 * O cliente *só* conhece a interface moderna 'ProcessadorTransacoes'.
 * Ele não faz ideia da existência do 'SistemaBancarioLegado' ou de 'HashMaps'.
 * Isso cumpre o Princípio da Inversão de Dependência (DIP).
 */
public class DemoClienteBancario {
    public static void main(String[] args) {
        
        // 1. Cria a instância do sistema legado (que não podemos mudar)
        SistemaBancarioLegado sistemaLegado = new SistemaBancarioLegado();

        // 2. Cria o nosso Adapter, "embrulhando" o sistema legado
        // O cliente depende da INTERFACE, não da classe concreta (DIP)
        ProcessadorTransacoes processador = new AdapterBancario(sistemaLegado);

        // 3. O cliente usa a interface MODERNA
        System.out.println("--- [CLIENTE] Chamada 1 (BRL) ---");
        RespostaAutorizacao resp1 = processador.autorizar("1234-5678-8765-4321", 500.0, "BRL");
        System.out.println("--- [CLIENTE] Resposta recebida: " + resp1 + "\n");
        
        System.out.println("--- [CLIENTE] Chamada 2 (USD - Valor Alto) ---");
        RespostaAutorizacao resp2 = processador.autorizar("9999-8888-7777-6666", 1500.0, "USD");
        System.out.println("--- [CLIENTE] Resposta recebida: " + resp2 + "\n");
    }
}
