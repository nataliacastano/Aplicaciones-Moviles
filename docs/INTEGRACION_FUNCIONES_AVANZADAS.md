# Funciones avanzadas — integración y revisión del miembro 5

Componentes reutilizables de cámara, galería y recorte, documentos PDF, mapas y direcciones, WhatsApp/Email, fechas y vencimientos, y gráficos económicos. Todo el código está en `ui/advanced/` y `data/media/`, `data/geo/`. Las pantallas de los demás miembros solo se han tocado en los puntos de integración indicados al final.

## Cómo integrar cada componente

### Miembro 4 — foto de perfil (cámara, galería y recorte)

`rememberImageCaptureFlow` (`ui/advanced/media/ImageCaptureFlow.kt`) hace todo el flujo: diálogo Cámara/Galería, captura o selección, ajuste con desplazamiento y zoom, y confirmación. Solo llama a `onConfirmed` después de Confirmar.

```kotlin
val flow = rememberImageCaptureFlow(storage, title = "Fotografía de perfil", circleGuide = true,
    onConfirmed = { recorte -> viewModel.cambiarFoto(Uri.fromFile(recorte).toString(), owner) },
    onRemove = if (tieneFoto) viewModel::eliminarFoto else null)
IconButton(onClick = flow.start, enabled = !flow.busy) { … }
```

- El archivo recibido es temporal: hay que copiarlo antes de que termine `onConfirmed` (es `suspend`).
- `PhotoAdjustmentScreen` y `PhotoSourceDialog` (del miembro 4) aceptan ahora `title`, `circleGuide`, `cameraAvailable` y `onRemove` con valores por defecto; Perfil sigue funcionando sin cambios.
- `LocalImage(path, …, placeholder = { … })` muestra la imagen predeterminada si no hay foto y «No se pudo cargar · Reintentar» si el archivo falla.

### Miembro 3 — documentos durante el alta de empleados

```kotlin
val pendientes = rememberPendingPdfs()
PendingPdfDocumentsEditor(pendientes.value, { pendientes.value = it }, enabled = !saving)
// Al guardar:
vm.save({ val id = addEmployee(empleado); attachPendingPdfs(storage, "EMPLEADO", id, pendientes.value) }, onBack)
```

- Tipos: contrato, certificado, formación, documentación laboral u otro, con descripción y caducidad opcional.
- Solo admite PDF válidos (`checkPdf` comprueba tipo, tamaño ≤ 20 MB y la cabecera `%PDF-`).
- Si un documento falla se deshace todo: ni filas sin archivo ni archivos sin fila.
- El formulario actual de alta valida los PDF por su cuenta; se puede sustituir por este componente.

### Miembro 3 — direcciones y zonas laborales

- `AddressAutocompleteField(value, onValueChange, onSelected)`: sugerencias reales mientras se escribe. `onSelected` recibe `AddressSuggestion` con calle, número, localidad, provincia, código postal, país, `latitude` y `longitude`. Si el usuario edita el texto después, hay que olvidar las coordenadas.
- `LocationPickerContent(...)`: dirección y mapa sincronizados; avisa antes de guardar datos contradictorios.
- `WorkZonePickerDialog(...)`: nombre y ubicación de una zona laboral. Las zonas se guardan con `dao.saveWorkZone(HomeWorkerMeta, HomeZoneLocation?)` y se borran con `dao.removeWorkZone(...)`.
- `state.workZonesOf(kind, id)` devuelve las zonas de un trabajador con su ubicación.

### Miembro 2 — campana 🔔, calendario de Inicio, fotos y gráficos

- `state.dueOverview(today, warnDays)` da vencidos, próximos, vigentes y documentación sin fecha con las mismas reglas que el calendario y las notificaciones. `bellCount` y `hasUrgent` sirven para la campana. `warnDays` viene de Ajustes (`AjustesApp.diasAntelacion`).
- `state.projectCosts(id)`, `state.monthlyValues(meses)`, `incomeByProject()`, `workersByTrade()` y `hoursByWorker()` dan los datos de los gráficos.
- `DateField` y `TimeField` (`ui/advanced/forms`) sirven para cualquier formulario: AAAA-MM-DD y HH:mm con validación.

## Errores encontrados y causas confirmadas

| Error | Causa técnica confirmada | Corrección |
| --- | --- | --- |
| Tras seleccionar una foto no había encuadre (empleados, proyectos). | Las fichas guardaban la imagen directamente. | Flujo común con ajuste y guardado solo tras Confirmar. |
| Imágenes rotas o huecos vacíos. | `LocalImage` no distinguía «sin foto» de «error». | Estados cargando / sin foto / error con reintento. |
| Se admitía cualquier archivo en la documentación laboral. | Selector `pdf + image/*` y sin validar el contenido. | Solo PDF en trabajadores, validación por contenido, sustituir. |
| Archivos parciales en la carpeta de la empresa. | `importUri` creaba el destino antes de leer el origen. | Se borra el parcial si la copia falla. |
| Documentos sin fila si fallaba la base de datos. | La copia y la inserción no se deshacían juntas. | Limpieza del archivo si falla la inserción. |
| Datos personales en copias y en la pantalla de bloqueo. | Sin reglas de copia; notificación pública. | Se excluyen los adjuntos de la nube y de la transferencia; notificación privada. |
| Dirección y marcador contradictorios. | Se guardaban por separado y sin comprobar. | Sincronización, geocodificación inversa y confirmación. |
| «WhatsApp + Email» incompleto. | `startActivities` lanzaba las dos apps a la vez; WhatsApp y Gmail abren su propia tarea y la segunda se perdía. | Envío paso a paso con estados reales. |
| Los cambios en una plantilla se perdían. | No había almacenamiento de plantillas. | `MessageTemplateStore` por empresa. |
| Horas como «8:00» o «25:00» aceptadas. | Campo de texto libre. | `TimeField` HH:mm y comprobación inicio/fin. |
| «Enviar ahora el aviso» poco comprensible. | Lanzaba el trabajo sin explicar qué hacía. | Vista previa (qué, a quién, canal, texto) y confirmación. |
| Resultado económico casi siempre negativo. | Gráficos restaba ingresos − gastos − horas − materiales: las horas y materiales se descontaban dos veces si también se registraban como gasto. | Resultado = ingresos − gastos registrados; coste estimado aparte. |
| Envíos rápidos de Mensajería sin vista previa. | Abrían WhatsApp o el correo directamente. | Pasan por el redactor. |
| Los tests no compilaban en `main`. | `ProfileRepositoryTest` usaba el constructor anterior de `RoomProfileRepository`. | Sustituto en memoria de `PerfilCuentaService`. |

## Dependencias y configuración

- Sin dependencias nuevas en esta revisión. Ya estaban `osmdroid` (mapa) y `work-runtime-ktx` (avisos).
- Direcciones: **Photon (komoot)** sobre OpenStreetMap, sin clave ni facturación. Necesita conexión. Google Places Autocomplete requeriría una clave de API y un proyecto de Google Cloud con facturación activada; para usarlo basta implementar `AddressProvider` y leer la clave de `local.properties` (nunca en el código).
- Base de datos de la empresa: versión 6 (`home_zone_locations`, migración automática desde la 5).
- Permisos: no se pide `CAMERA` ni almacenamiento (se usan la cámara y el selector del sistema). `POST_NOTIFICATIONS` solo para los avisos.

## Limitaciones técnicas

- **WhatsApp y el correo no informan de si el mensaje se envió.** La app muestra «Abierto para enviar · no verificado» y pregunta al usuario. No se automatiza ningún envío.
- El servidor público de Photon y las teselas de OpenStreetMap tienen límites de uso razonable; para un uso intensivo hace falta un servidor propio o un proveedor de pago.
- La provincia se deduce del código postal solo en España; fuera de España se usa la región que devuelve OpenStreetMap.
- Los gastos de categoría PERSONAL o MATERIALES no se suman al coste estimado (se suponen ya incluidos en horas y materiales usados). Si una empresa no registra horas, su coste de personal estimado será 0, aunque sí aparece en el resultado real.

## Pruebas realizadas

`./gradlew testDebugUnitTest`: 70 tests, 66 correctos. Los 4 que fallan son de otros módulos y ya fallaban antes de esta revisión (ver «Errores adicionales»). Los del módulo 5 pasan todos:

| Test | Qué comprueba |
| --- | --- |
| `DateTimeFieldsTest` (5) | Guiones y dos puntos automáticos, fechas reales, horas 00:00–23:59, fin posterior al inicio. |
| `PdfValidationTest` (1) | Detección de PDF por contenido, no por nombre. |
| `PendingPdfDocumentsTest` (2) | Asociación de los PDF con el empleado nuevo y vuelta atrás completa si uno falla. |
| `AddressSearchTest` (4) | Lectura de respuestas reales de Photon, provincias por código postal, coherencia dirección ↔ marcador. |
| `WorkZonesTest` (1) | Varias zonas con ubicación, borrado y borrado en cascada con el trabajador. |
| `TemplateStoreTest` (2) | Plantillas guardadas tras reabrir, separadas por empresa, restaurar. |
| `SendFlowTest` (3), `MessageFieldsTest` (2) | Envío paso a paso, estados sin «enviado» falso, validación de horas y fechas del mensaje. |
| `DueOverviewTest` (2), `NotifyNowTest` (2) | Vigente/próximo/vencido, recuento de la campana, texto del aviso igual al de la notificación. |
| `EconomyTest` (1), `AdvancedFeaturesTest` (9) | Resultado sin doble recuento, costes por proyecto y mes, plantillas, vencimientos. |
| `HomeDatabaseTest` (3) | Migraciones de la base de datos hasta la versión 6 conservando los datos. |

`./gradlew assembleDebug`: compila sin errores.

## Funciones que requieren pruebas manuales

No se han podido verificar en un dispositivo en esta revisión (sí con tests unitarios y compilando):

1. Cámara y galería con recorte en empleados, subcontratados, proyectos y materiales.
2. Selector de PDF filtrado, mensaje de archivo incompatible y «Sustituir».
3. Autocompletado de direcciones con conexión real y sin conexión.
4. Mover el marcador, propuesta de dirección y aviso de datos contradictorios.
5. Zonas laborales: añadir, ubicar, mapa de zonas y quitar.
6. WhatsApp + Email paso a paso con WhatsApp instalado (el emulador no lo tiene) y vuelta a la app.
7. Plantillas guardadas tras cerrar y abrir la app.
8. Selectores de lugar, hora (reloj de 24 h) y tipo de documento; vista previa y volver a editar.
9. Vista previa del aviso de vencimientos y notificación en la pantalla de bloqueo.
10. Gráficos con ingresos y gastos reales, y resultado negativo en rojo.

## Errores adicionales detectados (fuera del módulo 5)

- **Cuatro tests de otros módulos fallan en `main`**: `ProfileRepositoryTest` (falta la migración 4 → 7 de `AppDatabase`: riesgo de error al actualizar instalaciones antiguas), `cambiarFotoCopiaLaImagenYBorrarlaVuelveAlAvatar`, `ModuleCatalogTest.summarySeparatesUpcomingExpiredAndResolvedRecords` y `SettingsViewModelTest.cambioDePasswordInformaDelCorreoEnviado` (textos cambiados).
- **El formulario de ingresos y gastos empieza en «Gasto».** Si no se cambia el tipo, un cobro se guarda como gasto. Se recomienda que el miembro 3 obligue a elegir el tipo o use dos botones («Registrar ingreso» / «Registrar gasto»).
- **La campana de Inicio usa reglas distintas** (30 días fijos y «vencido» si vence hoy). Se recomienda que el miembro 2 use `dueOverview()` para que todos los contadores coincidan.
- El miembro 4 excluyó sus tests de la entrega; los del módulo 5 se mantienen versionados. Si el equipo decide excluirlos, están en `app/src/test/.../advanced`, `data/geo` y `data/media`.

## Archivos del módulo

- Nuevos: `ui/advanced/forms/DateTimeFields.kt`, `ui/advanced/media/ImageCaptureFlow.kt`, `data/media/PdfValidation.kt`, `ui/advanced/documents/PendingPdfDocuments.kt`, `data/geo/AddressSearch.kt`, `ui/advanced/map/AddressAutocompleteField.kt`, `ui/advanced/map/LocationPicker.kt`, `ui/advanced/map/WorkZones.kt`, `ui/advanced/communication/TemplateStore.kt`, `SendFlow.kt`, `MessageFieldEditors.kt`, `ui/advanced/dates/NotifyNowDialog.kt`.
- Modificados del módulo: fotos, documentos, mapa, mensajes, fechas, gráficos, `AttachmentStorage`, `HomeDatabase` (v6), `res/xml/data_extraction_rules.xml`.
- Puntos de integración en pantallas de otros miembros: `HomeListsContent` (plantillas, limpieza de temporales, calendario), `MainScaffold` (preferencias de avisos), `MessagingScreen` (plantillas y vista previa), `HomeMenu` (nombre «Mapa»), `DetailViewModel` (resultado económico), `PhotoAdjustmentScreen` (parámetros opcionales).
