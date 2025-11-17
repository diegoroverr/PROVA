import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.*;

// --- 1. OBJETOS DE DADOS E EXCEÇÃO ---

/**
 * Decisão: O documento que será passado pela cadeia.
 */
class DocumentoNFe {
    private String xmlContent;
    public DocumentoNFe(String xmlContent) { this.xmlContent = xmlContent; }
    public String getXmlContent() { return xmlContent; }
    // Flag para simular modificação de estado (ex: inserção em DB)
    public boolean foiInseridoNoDB = false; 
}

/**
 * Decisão: Um "Context Object" é crucial. Ele carrega o estado
 * compartilhado pela cadeia (erros, documento, pilha de rollback).
 */
class ValidationContext {
    private DocumentoNFe documento;
    private List<String> errors = new ArrayList<>();
    // A pilha de "desfazer" (undo) para o requisito de rollback
    private Stack<IValidador> undoStack = new Stack<>();

    public ValidationContext(DocumentoNFe doc) { this.documento = doc; }
    
    public DocumentoNFe getDocumento() { return documento; }
    public void addError(String error) { this.errors.add(error); }
    public List<String> getErrors() { return errors; }
    public boolean hasErrors() { return !errors.isEmpty(); }

    // Métodos para o Pipeline gerenciar o Rollback
    public void pushUndo(IValidador validador) { this.undoStack.push(validador); }
    public IValidador popUndo() { return this.undoStack.pop(); }
    public boolean isUndoStackEmpty() { return this.undoStack.isEmpty(); }
}


// --- 2. A INTERFACE DO HANDLER (CHAIN OF RESPONSIBILITY) ---

/**
 * Decisão: A interface 'Handler' (IValidador).
 * Define os métodos 'validar' e 'rollback'.
 * O 'rollback' é o mecanismo de "desfazer" para validadores que modificam estado.
 */
interface IValidador {
    String getNome(); // Para logging e lógica condicional
    void validar(ValidationContext context);
    void rollback(ValidationContext context); // Para o requisito de rollback
}


// --- 3. O ORQUESTRADOR (PIPELINE) ---

/**
 * Decisão: O 'Pipeline' (Orquestrador) que gerencia a cadeia.
 * Esta classe implementa a lógica complexa (Circuit Breaker, Timeout, Rollback).
 */
class ValidationPipeline {
    private final List<IValidador> validadores = new ArrayList<>();
    private final ExecutorService executor;
    
    // Constantes para os requisitos
    private final long TIMEOUT_PER_VALIDATOR_MS = 1000;
    private final int CIRCUIT_BREAKER_LIMIT = 3;

    public ValidationPipeline(ExecutorService executor) {
        this.executor = executor;
    }

    public void addValidador(IValidador validador) {
        this.validadores.add(validador);
    }

    public boolean execute(ValidationContext context) {
        try {
            for (IValidador validador : validadores) {
                
                // --- REQUISITO: CIRCUIT BREAKER (3 falhas) ---
                if (context.getErrors().size() >= CIRCUIT_BREAKER_LIMIT) {
                    System.out.println("[PIPELINE] CIRCUIT BREAKER ATIVADO! Parando a cadeia.");
                    break; 
                }

                // --- REQUISITO: VALIDAÇÃO CONDICIONAL (Restrição 1) ---
                // Pular 3 (Fiscais) e 5 (SEFAZ) se já houver erros
                if (validador.getNome().equals("ValidadorRegrasFiscais") || 
                    validador.getNome().equals("ValidadorServicoSEFAZ")) {
                    if (context.hasErrors()) {
                        System.out.println("[PIPELINE] Pulando '" + validador.getNome() + "' pois erros anteriores existem.");
                        continue;
                    }
                }
                
                // --- REQUISITO: TIMEOUT INDIVIDUAL (Restrição 3) ---
                System.out.println("[PIPELINE] Executando: " + validador.getNome());
                
                // Criamos uma "tarefa" para executar o validador
                Callable<Void> task = () -> {
                    validador.validar(context);
                    return null;
                };
                
                Future<Void> future = executor.submit(task);

                try {
                    // Espera pelo resultado, mas com timeout
                    future.get(TIMEOUT_PER_VALIDATOR_MS, TimeUnit.MILLISECONDS);
                    
                    // Se o validador executou sem exceção, ele é "empilhado" para
                    // um possível rollback futuro.
                    context.pushUndo(validador);

                } catch (TimeoutException e) {
                    future.cancel(true); // Interrompe a tarefa
                    context.addError(validador.getNome() + ": TIMEOUT (excedeu " + TIMEOUT_PER_VALIDATOR_MS + "ms)");
                } catch (Exception e) {
                    // Captura exceções da própria validação
                    context.addError(validador.getNome() + ": FALHA INTERNA (" + e.getMessage() + ")");
                }
            }

        } catch (Exception e) {
            context.addError("Erro fatal no pipeline: " + e.getMessage());
        }

        // Se terminamos o loop e há erros, precisamos fazer rollback
        if (context.hasErrors()) {
            System.out.println("[PIPELINE] Falha na validação. Iniciando rollback...");
            triggerRollback(context);
        }

        return !context.hasErrors();
    }
    
    /**
     * Decisão: Lógica de Rollback (Requisito 4 e Restrição 2).
     * Itera a pilha de "undo" ao contrário, chamando 'rollback'
     * em cada validador que foi executado com sucesso.
     */
    private void triggerRollback(ValidationContext context) {
        while (!context.isUndoStackEmpty()) {
            IValidador validador = context.popUndo();
            System.out.println("[PIPELINE] Chamando rollback em: " + validador.getNome());
            validador.rollback(context); // Chama o método de rollback
        }
    }
}


// --- 4. VALIDADORES CONCRETOS (HANDLERS) ---
// (Implementações Dummy)

class ValidadorSchemaXML implements IValidador {
    public String getNome() { return "ValidadorSchemaXML"; }
    public void validar(ValidationContext context) {
        System.out.println("  > 1. Validando Schema XSD... OK.");
    }
    public void rollback(ValidationContext context) { /* Não faz nada */ }
}

class ValidadorCertificadoDigital implements IValidador {
    public String getNome() { return "ValidadorCertificadoDigital"; }
    public void validar(ValidationContext context) {
        System.out.println("  > 2. Validando Certificado... OK.");
        // Simulando uma falha para o Circuit Breaker
        // context.addError("Certificado revogado"); 
    }
    public void rollback(ValidationContext context) { /* Não faz nada */ }
}

class ValidadorRegrasFiscais implements IValidador {
    public String getNome() { return "ValidadorRegrasFiscais"; }
    public void validar(ValidationContext context) {
        System.out.println("  > 3. Validando Impostos... OK.");
        
        // Simulando uma falha grave que deve parar o processo
        // throw new RuntimeException("Cálculo de IPI falhou"); 
    }
    public void rollback(ValidationContext context) { /* Não faz nada */ }
}

class ValidadorBancoDados implements IValidador {
    public String getNome() { return "ValidadorBancoDados"; }
    
    public void validar(ValidationContext context) {
        System.out.println("  > 4. Verificando duplicidade no DB...");
        // Simula a modificação de estado (inserção)
        System.out.println("    -> Inserindo chave " + context.getDocumento().getXmlContent() + " (pendente)");
        context.getDocumento().foiInseridoNoDB = true;
    }

    /**
     * Decisão: Implementação do Rollback (Restrição 2).
     * Este é o único validador que *realmente* faz um rollback.
     */
    public void rollback(ValidationContext context) {
        if (context.getDocumento().foiInseridoNoDB) {
            System.out.println("    -> ROLLBACK DB: Removendo chave " + context.getDocumento().getXmlContent());
            context.getDocumento().foiInseridoNoDB = false;
        }
    }
}

class ValidadorServicoSEFAZ implements IValidador {
    private boolean simularFalha;
    public ValidadorServicoSEFAZ(boolean simularFalha) { this.simularFalha = simularFalha; }
    
    public String getNome() { return "ValidadorServicoSEFAZ"; }
    
    public void validar(ValidationContext context) {
        System.out.println("  > 5. Consultando SEFAZ (online)...");
        if (simularFalha) {
            context.addError("SEFAZ indisponível (Erro 503)");
            System.out.println("    -> FALHA NA SEFAZ!");
        } else {
            System.out.println("    -> SEFAZ OK.");
        }
    }
    public void rollback(ValidationContext context) { /* Não faz nada */ }
}


// --- 5. O CLIENTE (DEMONSTRAÇÃO) ---

public class DemoValidacaoNFe {
    public static void main(String[] args) {
        // O Executor é necessário para o requisito de Timeout
        ExecutorService executor = Executors.newCachedThreadPool();

        // --- CENÁRIO 1: FALHA NA ÚLTIMA ETAPA (GATILHA O ROLLBACK) ---
        System.out.println("--- INICIANDO CENÁRIO 1: FALHA NA SEFAZ (DEVE FAZER ROLLBACK) ---");
        
        ValidationPipeline pipelineFalha = new ValidationPipeline(executor);
        pipelineFalha.addValidador(new ValidadorSchemaXML());
        pipelineFalha.addValidador(new ValidadorCertificadoDigital());
        pipelineFalha.addValidador(new ValidadorRegrasFiscais());
        pipelineFalha.addValidador(new ValidadorBancoDados()); // Este fará rollback
        pipelineFalha.addValidador(new ValidadorServicoSEFAZ(true)); // Simula falha

        DocumentoNFe doc1 = new DocumentoNFe("NFE-12345");
        ValidationContext ctx1 = new ValidationContext(doc1);
        
        boolean sucesso1 = pipelineFalha.execute(ctx1);
        
        System.out.println("\n[CLIENTE] Resultado Cenário 1: " + (sucesso1 ? "SUCESSO" : "FALHA"));
        System.out.println("[CLIENTE] Erros: " + ctx1.getErrors());
        System.out.println("[CLIENTE] Doc inserido no DB? " + doc1.foiInseridoNoDB); // Deve ser 'false'


        // --- CENÁRIO 2: SUCESSO COMPLETO ---
        System.out.println("\n\n--- INICIANDO CENÁRIO 2: SUCESSO ---");
        
        ValidationPipeline pipelineSucesso = new ValidationPipeline(executor);
        pipelineSucesso.addValidador(new ValidadorSchemaXML());
        pipelineSucesso.addValidador(new ValidadorCertificadoDigital());
        pipelineSucesso.addValidador(new ValidadorRegrasFiscais());
        pipelineSucesso.addValidador(new ValidadorBancoDados());
        pipelineSucesso.addValidador(new ValidadorServicoSEFAZ(false)); // Sem falha

        DocumentoNFe doc2 = new DocumentoNFe("NFE-67890");
        ValidationContext ctx2 = new ValidationContext(doc2);
        
        boolean sucesso2 = pipelineSucesso.execute(ctx2);
        
        System.out.println("\n[CLIENTE] Resultado Cenário 2: " + (sucesso2 ? "SUCESSO" : "FALHA"));
        System.out.println("[CLIENTE] Erros: " + ctx2.getErrors());
        System.out.println("[CLIENTE] Doc inserido no DB? " + doc2.foiInseridoNoDB); // Deve ser 'true'
        
        executor.shutdown();
    }
}
