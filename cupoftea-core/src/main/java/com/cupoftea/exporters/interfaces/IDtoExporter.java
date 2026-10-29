package com.cupoftea.exporters.interfaces;

import java.util.List;

public interface IDtoExporter {

    <T> List<T> exportToDtos(Class<T> dtoClass);

}
