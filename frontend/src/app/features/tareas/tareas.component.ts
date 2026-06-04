import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { FileUploadModule } from 'primeng/fileupload';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import type { Tarea } from '../../core/models';

@Component({
  selector: 'app-tareas',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, CardModule, ButtonModule, TagModule, FileUploadModule, ToastModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />

    <div class="page-header">
      <h2>Tareas</h2>
      <p class="subtitle">Gestión de tareas y entregas</p>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:1rem;margin-bottom:2rem">
      <p-card styleClass="stat-card">
        <ng-template pTemplate="header">
          <div style="color:#64748b;font-size:0.85rem;font-weight:600">Tareas Activas</div>
        </ng-template>
        <div style="font-size:2.5rem;color:#1d4ed8;font-weight:700">{{ tareas().length }}</div>
      </p-card>

      <p-card styleClass="stat-card">
        <ng-template pTemplate="header">
          <div style="color:#64748b;font-size:0.85rem;font-weight:600">Entregas Pendientes</div>
        </ng-template>
        <div style="font-size:2.5rem;color:#d97706;font-weight:700">{{ pendientes() }}</div>
      </p-card>
    </div>

    <div style="margin-bottom:1rem">
      <h3>Tareas del Ciclo</h3>
      <p-table [value]="tareas()" styleClass="p-datatable-sm">
        <ng-template pTemplate="header">
          <tr>
            <th pSortableColumn="titulo">Tarea <p-sortIcon field="titulo" /></th>
            <th pSortableColumn="fecha_entrega" style="width:120px">Entrega <p-sortIcon field="fecha_entrega" /></th>
            <th style="width:100px">Puntaje Máx.</th>
            <th style="width:100px">Acciones</th>
          </tr>
        </ng-template>

        <ng-template pTemplate="body" let-tarea>
          <tr>
            <td>{{ tarea.titulo }}</td>
            <td>{{ tarea.fecha_entrega }}</td>
            <td style="text-align:center">{{ tarea.puntaje_maximo }}</td>
            <td>
              <p-button
                icon="pi pi-search"
                [rounded]="true"
                [text]="true"
                [plain]="true"
                pTooltip="Ver entregas"
              />
            </td>
          </tr>
        </ng-template>

        <ng-template pTemplate="emptymessage">
          <tr><td [colSpan]="4">No hay tareas para este ciclo</td></tr>
        </ng-template>
      </p-table>
    </div>

    <div>
      <h3>Mis Entregas Pendientes</h3>
      <p-table [value]="entregas()" styleClass="p-datatable-sm">
        <ng-template pTemplate="header">
          <tr>
            <th>Tarea</th>
            <th style="width:120px">Vence</th>
            <th style="width:80px">Estado</th>
            <th style="width:150px">Acción</th>
          </tr>
        </ng-template>

        <ng-template pTemplate="body" let-entrega>
          <tr>
            <td>{{ entrega.titulo }}</td>
            <td>{{ entrega.fecha }}</td>
            <td>
              <p-tag
                [value]="entrega.estado"
                [severity]="entrega.estado === 'PENDIENTE' ? 'warn' : 'info'"
              />
            </td>
            <td>
              <p-button
                label="Subir archivo"
                icon="pi pi-upload"
                [size]="'small'"
                (onClick)="showUploadDialog(entrega)"
              />
            </td>
          </tr>
        </ng-template>

        <ng-template pTemplate="emptymessage">
          <tr><td [colSpan]="4">Todas las entregas completadas</td></tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: [`
    .page-header { margin-bottom: 1.5rem; }
    .subtitle { color: #64748b; font-size: 0.85rem; margin: 0.25rem 0 0; }
    h3 { color: #1e293b; margin-bottom: 1rem; margin-top: 2rem; }
    :host ::ng-deep .stat-card { text-align: center; }
  `],
})
export class TareasComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly ctx = inject(ContextService);
  private readonly msg = inject(MessageService);

  tareas = signal<Tarea[]>([]);
  entregas = signal<any[]>([]);
  pendientes = () => this.entregas().filter(e => e.estado === 'PENDIENTE').length;

  ngOnInit(): void {
    this.api.get<Tarea[]>('/tareas')
      .subscribe(t => {
        this.tareas.set(t);
        // Mock entregas pendientes
        this.entregas.set(t.map(task => ({
          titulo: task.titulo,
          fecha: task.fecha_entrega,
          estado: Math.random() > 0.5 ? 'PENDIENTE' : 'ENTREGADO',
        })).slice(0, 5));
      });
  }

  showUploadDialog(entrega: any): void {
    this.msg.add({ severity: 'info', summary: 'Upload', detail: `Subir entrega de: ${entrega.titulo}` });
  }
}
