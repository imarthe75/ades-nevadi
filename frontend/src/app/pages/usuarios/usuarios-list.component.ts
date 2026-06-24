import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApexComponentsModule } from '../../shared/apex-components.module';
import { GridColumn } from 'apex-component-library';
import { of } from 'rxjs';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';

/**
 * Listado de usuarios del sistema (personal docente y administrativo).
 * Vista de administración con grid interactivo al estilo APEX.
 * Requiere nivelAcceso 5 (AdminSistema) para gestionar usuarios de todos los planteles.
 */
@Component({
  selector: 'app-usuarios-list',
  templateUrl: './usuarios-list.component.html',
  styleUrls: ['./usuarios-list.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, ApexComponentsModule, CardModule, ButtonModule]
})
export class UsuariosListComponent implements OnInit {
  
  // Mock data for MVP
  usuarios$ = of([
    { id: 1, nombre: 'Ana Gomez', email: 'ana@ejemplo.com', rol_id: 'Admin', creado_en: '2026-06-01' },
    { id: 2, nombre: 'Juan Perez', email: 'juan@ejemplo.com', rol_id: 'User', creado_en: '2026-06-02' }
  ]);
  
  columns: GridColumn[] = [
    {
      field: 'id',
      header: 'ID',
      type: 'text',
      sortable: true,
      filterable: true,
      width: '100px'
    },
    {
      field: 'nombre',
      header: 'Nombre',
      type: 'text',
      sortable: true,
      filterable: true,
      editable: true
    },
    {
      field: 'email',
      header: 'Email',
      type: 'text',
      sortable: true,
      filterable: true,
      editable: true
    },
    {
      field: 'rol_id',
      header: 'Rol',
      type: 'text',
      sortable: true,
      filterable: true
    },
    {
      field: 'creado_en',
      header: 'Creado',
      type: 'date',
      sortable: true,
      width: '150px'
    }
  ];
  
  constructor() {}
  
  ngOnInit(): void {}
  
  onCellEdit(event: any): void {
    console.log('Saved change for cell', event);
  }

  openNewModal(): void {
    console.log('Open new modal');
  }
}
