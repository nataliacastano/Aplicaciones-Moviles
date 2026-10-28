package com.example.gestorempresa.ui.home

import com.example.gestorempresa.data.local.HomeDao

/** ViewModel de Inicio y listados: observa los registros de la empresa autenticada.
 * La lógica compartida de resúmenes, filtros y guardado reside en HomeWorkspaceViewModel.
 */
class HomeViewModel(dao: HomeDao) : HomeWorkspaceViewModel(dao)
