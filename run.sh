#!/bin/bash

echo "=========================================="
echo "  Sistema de Monitoramento Ambiental"
echo "=========================================="
echo ""

# Criar diretório bin se não existir
mkdir -p bin

echo "[1] Compilando projeto..."
find src -name "*.java" > sources.txt
javac -d bin @sources.txt
COMPILE_STATUS=$?

if [ $COMPILE_STATUS -eq 0 ]; then
    echo "    ✓ Compilação concluída com sucesso!"
    echo ""
    
    # Limpar arquivo temporário
    rm sources.txt
    
    echo "[2] Iniciando simulação..."
    echo ""
    
    java -cp bin Main
    
else
    echo "    ✗ Erro na compilação!"
    rm sources.txt
    exit 1
fi

