package com.cupoftea.importers.interfaces;

import java.util.Collection;

public interface IDtoImporter {

    <T> void importFromDtos(Collection<T> dtos);

}
