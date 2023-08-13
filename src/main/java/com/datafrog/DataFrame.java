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
}