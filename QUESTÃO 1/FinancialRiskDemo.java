// --- ETAPA 1: A ABSTRAÇÃO (A INTERFACE STRATEGY) ---
/**
 * Decisão: Esta é a interface Strategy.
 * Define o contrato que todos os algoritmos de risco devem seguir.
 * Garante o princípio de Inversão de Dependência (DIP) e Aberto/Fechado (OCP).
 */
interface RiskAnalysisStrategy {
    void calculate(FinancialDataContext context);
}


// --- ETAPA 2: O OBJETO DE DADOS (CONTEXTO COMPLEXO) ---
/**
 * Decisão: Encapsula os "múltiplos parâmetros financeiros" em um POJO.
 * Isso simplifica a passagem de dados para as estratégias.
 */
class FinancialDataContext {
    private double portfolioValue;
    private double volatility;

    public FinancialDataContext(double portfolioValue, double volatility) {
        this.portfolioValue = portfolioValue;
        this.volatility = volatility;
    }
    
    // Getters para as estratégias usarem...
    public double getPortfolioValue() { return portfolioValue; }
    public double getVolatility() { return volatility; }

    @Override
    public String toString() {
        return "Contexto[Portfolio=" + portfolioValue + ", Volatilidade=" + volatility + "]";
    }
}


// --- ETAPA 3: AS ESTRATÉGIAS CONCRETAS (OS ALGORITMOS) ---
/**
 * Decisão: Estratégia Concreta 1 (Implementação Dummy).
 * Cumpre o Princípio da Responsabilidade Única (SRP).
 */
class ValueAtRiskStrategy implements RiskAnalysisStrategy {
    @Override
    public void calculate(FinancialDataContext context) {
        // Cálculo dummy
        System.out.println("CÁLCULO [VaR]: Calculando Value at Risk para " + context);
    }
}

/**
 * Decisão: Estratégia Concreta 2 (Implementação Dummy).
 * Intercambiável com qualquer outra 'RiskAnalysisStrategy'.
 */
class ExpectedShortfallStrategy implements RiskAnalysisStrategy {
    @Override
    public void calculate(FinancialDataContext context) {
        // Cálculo dummy
        System.out.println("CÁLCULO [ES]: Calculando Expected Shortfall para " + context);
    }
}

/**
 * Decisão: Estratégia Concreta 3 (Implementação Dummy).
 * Adicionada sem modificar o RiskProcessor (OCP).
 */
class StressTestingStrategy implements RiskAnalysisStrategy {
    @Override
    public void calculate(FinancialDataContext context) {
        // Cálculo dummy
        System.out.println("CÁLCULO [Stress]: Executando Stress Test para " + context);
    }
}


// --- ETAPA 4: O CONTEXTO (O PROCESSADOR) ---
/**
 * Decisão: Esta é a classe 'Contexto' do padrão Strategy.
 * 1. Armazena os dados complexos (FinancialDataContext).
 * 2. Mantém uma referência à *interface* da estratégia.
 * 3. Permite a troca de estratégia em tempo de execução (setStrategy).
 */
class RiskProcessor {
    
    private RiskAnalysisStrategy strategy; // Referência à abstração (DIP)
    private FinancialDataContext data;

    public RiskProcessor(FinancialDataContext data) {
        this.data = data;
    }

    /**
     * Ponto principal do requisito: permite a troca da estratégia
     * em tempo de execução pelo cliente.
     */
    public void setStrategy(RiskAnalysisStrategy strategy) {
        System.out.println("\n[SISTEMA] Trocando algoritmo para: " + strategy.getClass().getSimpleName());
        this.strategy = strategy;
    }

    /**
     * O cliente chama este método. O processador *delega* a execução
     * para a estratégia concreta atual.
     */
    public void performRiskAnalysis() {
        if (strategy == null) {
            System.out.println("[SISTEMA] ERRO: Nenhuma estratégia de análise foi definida.");
            return;
        }
        // Delegação da chamada para o algoritmo encapsulado
        this.strategy.calculate(this.data);
    }
}


// --- ETAPA 5: O CLIENTE (DEMONSTRAÇÃO) ---
/**
 * Classe principal que atua como o 'Cliente'.
 * O Cliente decide *quando* trocar a estratégia, mas não sabe *como*
 * cada estratégia funciona internamente.
 */
public class FinancialRiskDemo {
    public static void main(String[] args) {
        
        // 1. O "contexto complexo" é criado
        FinancialDataContext data = new FinancialDataContext(5000000.0, 0.25);

        // 2. O processador é criado com esse contexto
        RiskProcessor processor = new RiskProcessor(data);

        // 3. REQUISITO DE NEGÓCIO 1: Calcular VaR
        processor.setStrategy(new ValueAtRiskStrategy());
        processor.performRiskAnalysis();

        // 4. REQUISITO DE NEGÓCIO 2: Mudar para Stress Test (troca dinâmica)
        processor.setStrategy(new StressTestingStrategy());
        processor.performRiskAnalysis();

        // 5. REQUISITO DE NEGÓCIO 3: Mudar para Expected Shortfall (troca dinâmica)
        processor.setStrategy(new ExpectedShortfallStrategy());
        processor.performRiskAnalysis();
    }
}
