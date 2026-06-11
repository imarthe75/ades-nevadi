import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, TemplateRef, ContentChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ListboxModule } from 'primeng/listbox';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'apex-list',
  standalone: true,
  imports: [CommonModule, ListboxModule, FormsModule],
  template: `
    <div class="apex-list-wrapper">
      <h3 *ngIf="title" class="apex-list-title">{{title}}</h3>
      <p-listbox 
        [options]="options" 
        [(ngModel)]="selection" 
        (onChange)="onChange.emit($event.value)"
        [multiple]="multiple"
        [filter]="filter"
        [checkbox]="checkbox"
        [optionLabel]="optionLabel"
        [listStyle]="listStyle"
        styleClass="apex-p-listbox">
        <ng-template let-item pTemplate="item">
          <ng-container *ngIf="itemTemplate; else defaultItem">
            <ng-container *ngTemplateOutlet="itemTemplate; context: {$implicit: item}"></ng-container>
          </ng-container>
          <ng-template #defaultItem>
            <div class="apex-list-item-default">
              <i *ngIf="item.icon" [class]="item.icon" style="margin-right: 0.5rem"></i>
              <span>{{optionLabel ? item[optionLabel] : item.label}}</span>
            </div>
          </ng-template>
        </ng-template>
      </p-listbox>
    </div>
  `,
  styles: [`
    .apex-list-wrapper {
      width: 100%;
    }
    .apex-list-title {
      font-size: 1.1rem;
      font-weight: 600;
      margin-bottom: 0.5rem;
      color: var(--text-color);
    }
    .apex-list-item-default {
      display: flex;
      align-items: center;
      padding: 0.25rem 0;
    }
    ::ng-deep .apex-p-listbox {
      width: 100%;
      border-radius: var(--border-radius);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexListComponent {
  @Input() options: any[] = [];
  @Input() selection: any;
  @Input() multiple: boolean = false;
  @Input() filter: boolean = false;
  @Input() checkbox: boolean = false;
  @Input() optionLabel: string = 'label';
  @Input() title?: string;
  @Input() listStyle: any = {'max-height': '250px'};

  @Output() onChange = new EventEmitter<any>();

  @ContentChild('item') itemTemplate?: TemplateRef<any>;
}
