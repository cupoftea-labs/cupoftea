package com.datafrog;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;


public class DataFrame {

    // ToDo ajustar tamanho dos headers para ter o mesmo numero de colunas que o body
    // ToDo Criar classe do tipo Header?
    @Getter @Setter
    private ArrayList<String> headers;
    public DataFrame() {
        this.headers = new ArrayList<>();
    }

    public void addColumn(String columnName) {
        headers.add(columnName);
        // ToDo procurar forma mais eficiente de preencher esses dados
        for (int i = 0; i < dataFrame.size(); i++) {
            dataFrame.get(i).add(null);
        }
        // ToDo lidar com casos em que é utilizado a mesma variável para criar colunas -> Evitar que ao editar uma linha outras sejam editadas ao mesmo tempo
    }

    public void removeColumn(String columnName) {
        // ToDo lidar com colunas com mesmo nome? ou remover apenas a primeira que aparece
        // ToDo Tratar caso nao exista a coluna
        int columnIndex = headers.indexOf(columnName);
        removeColumn(columnIndex);
    }
    public void removeColumn(int index) {
        // ToDo tratar out of bond?
        headers.remove(index);
        for (int i = 0; i < dataFrame.size(); i++) {
            dataFrame.get(i).remove(index);
        }
    }

}