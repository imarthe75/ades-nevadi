import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { providePrimeNG } from 'primeng/config';
import { definePreset } from '@primeng/themes';
import Aura from '@primeng/themes/aura';
import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es-MX';
import { MessageService } from 'primeng/api';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';

registerLocaleData(localeEs);

// ── Sistema triádico Instituto Nevadi ─────────────────────────────────────────
// Triada A (H=355°): Rojo primario  #D02030
// Triada B (H=115°): Verde sage     — éxito / aprobado
// Triada C (H=235°): Pizarra fría   — info / UI neutral (reemplaza azul genérico)
const NevadiPreset = definePreset(Aura, {
  semantic: {
    // Triada A — Rojo Nevadi
    primary: {
      50: '#fef2f3',
      100: '#fde0e2',
      200: '#fba7ac',
      300: '#f57580',
      400: '#ec4558',
      500: '#D02030',   // ← Nevadi Red
      600: '#b01c28',
      700: '#8c1620',
      800: '#681019',
      900: '#440b11',
      950: '#220508',
    },
    // Triada C — Pizarra fría (H=235° muy desaturado): reemplaza cualquier azul genérico
    colorScheme: {
      light: {
        surface: {
          0: '#ffffff',
          50: '#F8F9FA',
          100: '#F1F3F5',
          200: '#E9ECEF',
          300: '#DEE2E6',
          400: '#CED4DA',
          500: '#ADB5BD',
          600: '#868E96',
          700: '#495057',
          800: '#343A40',
          900: '#212529',
          950: '#141929',   // ← fondo login — triada C oscuro
        },
        primary: {
          color: '#D02030',
          contrastColor: '#ffffff',
          hoverColor: '#B01C28',
          activeColor: '#8C1620',
        },
      },
    },
  },
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimationsAsync(),
    providePrimeNG({
      theme: {
        preset: NevadiPreset,
        options: { prefix: 'p', darkModeSelector: '.dark-mode', cssLayer: false },
      },
      ripple: true,
      overlayAppendTo: 'body',
      translation: {
        accept: 'Aceptar',
        reject: 'Cancelar',
        choose: 'Seleccionar',
        upload: 'Subir',
        cancel: 'Cancelar',
        today: 'Hoy',
        clear: 'Limpiar',
        apply: 'Aplicar',
        weekHeader: 'Sm',
        firstDayOfWeek: 1,
        dateFormat: 'dd/mm/yy',
        dayNames: ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'],
        dayNamesShort: ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'],
        dayNamesMin: ['D', 'L', 'M', 'X', 'J', 'V', 'S'],
        monthNames: ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'],
        monthNamesShort: ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'],
        emptyMessage: 'Sin resultados',
        emptyFilterMessage: 'Sin resultados',
        emptySelectionMessage: 'Sin selección',
        emptySearchMessage: 'Sin resultados',
        searchMessage: '{0} resultados disponibles',
        selectionMessage: '{0} elementos seleccionados',
        aria: {
          trueLabel: 'Verdadero',
          falseLabel: 'Falso',
          nullLabel: 'Sin selección',
          star: '1 estrella',
          stars: '{star} estrellas',
          selectAll: 'Seleccionar todos',
          unselectAll: 'Deseleccionar todos',
          close: 'Cerrar',
          previous: 'Anterior',
          next: 'Siguiente',
          navigation: 'Navegación',
          scrollTop: 'Ir al inicio',
          moveTop: 'Mover al inicio',
          moveUp: 'Subir',
          moveDown: 'Bajar',
          moveBottom: 'Mover al final',
          moveToTarget: 'Mover al destino',
          moveToSource: 'Mover al origen',
          moveAllToTarget: 'Mover todo al destino',
          moveAllToSource: 'Mover todo al origen',
          pageLabel: 'Página {page}',
          firstPageLabel: 'Primera página',
          lastPageLabel: 'Última página',
          nextPageLabel: 'Página siguiente',
          previousPageLabel: 'Página anterior',
          rowsPerPageLabel: 'Filas por página',
          jumpToPageDropdownLabel: 'Ir a página',
          jumpToPageInputLabel: 'Ir a página',
          selectRow: 'Fila seleccionada',
          unselectRow: 'Fila deseleccionada',
          expandRow: 'Expandir fila',
          collapseRow: 'Contraer fila',
          showFilterMenu: 'Mostrar filtros',
          hideFilterMenu: 'Ocultar filtros',
          filterOperator: 'Operador de filtro',
          filterConstraint: 'Restricción de filtro',
          editRow: 'Editar fila',
          saveEdit: 'Guardar',
          cancelEdit: 'Cancelar',
          listView: 'Vista lista',
          gridView: 'Vista cuadrícula',
        },
      },
    }),
    { provide: LOCALE_ID, useValue: 'es-MX' },
    MessageService,
  ],
};
