# Mapeo: Oracle APEX 26.1.0 → Angular 19+ + PrimeNG 21+

## Componentes Principales

| APEX Component | Angular Component | PrimeNG | Notes |
|---|---|---|---|
| Alert Region | AlertComponent | p-message, p-toast | Toast notifications, dismissible alerts |
| Badges List | BadgeComponent | p-tag | Badge display, categories |
| Breadcrumb | BreadcrumbComponent | p-breadcrumb | Navigation history |
| Button | ButtonComponent | p-button | Variantes: primary, secondary, danger |
| Card | CardComponent | p-card | Contenedor con header/footer |
| Chart | ChartComponent | p-chart (Chart.js) | Gráficos: línea, barra, pie, etc. |
| Interactive Grid | DataGridComponent | p-dataTable + custom | Edición inline, sorting, filtering |
| Form | FormComponent | Reactive Forms | Validación, grupos, arrays |
| Modal Dialog | DialogComponent | p-dialog | Modal, drawer, sidebar |
| Navigation | NavigationComponent | p-menu, p-toolbar | Sidebar, top nav, contextual |
| Popup LOV | AutocompleteComponent | p-autoComplete | Búsqueda con dropdown |
| Report | TableComponent | p-dataTable | Solo lectura, exporte, paginación |
| Rich Text Editor | EditorComponent | p-editor | WYSIWYG, markdown support |
| Search | SearchComponent | p-inputSearch | Input con debounce, suggestions |
| Shuttle | TransferComponent | p-pickList, p-transfer | Movimiento bidireccional |
| Slider | SliderComponent | p-slider | Range slider, dual handle |
| Spinner | SpinnerComponent | p-spinner, p-progressSpinner | Input numérico, loading |
| Tabs | TabsComponent | p-tabView | Navegación por tabs |
| Timeline | TimelineComponent | p-timeline | Eventos secuenciales |
| Tree | TreeComponent | p-tree | Estructura jerárquica, expandible |

## Especiales (APEX Features)

| APEX Feature | Implementation | Notes |
|---|---|---|
| Data Reporter | ReportComponent + pgvector | Análisis, exportación, scheduled reports |
| AI Interactive Reports | AIReportComponent + Anthropic API | Insights automáticos, recomendaciones |
| Dynamic Actions | @HostListener, RxJS | Eventos reactivos |
| Validations | Validators (sync/async) | Real-time, server-side |
| Themes | CSS Variables + TailwindCSS | Light/dark, custom |

---

## Implementación Priority

**Fase 1 (MVP)**:
- Alert
- Breadcrumb
- Interactive Grid
- Data Reporter
- AI Interactive Report
