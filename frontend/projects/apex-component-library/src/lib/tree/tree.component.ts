import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TreeModule } from 'primeng/tree';
import { TreeNode } from 'primeng/api';

@Component({
  selector: 'apex-tree',
  standalone: true,
  imports: [CommonModule, TreeModule],
  template: `
    <div class="apex-tree-container">
      <h3 *ngIf="title" class="apex-tree-title">{{title}}</h3>
      <p-tree 
        [value]="value" 
        [selectionMode]="selectionMode" 
        [(selection)]="selection"
        (selectionChange)="onSelectionChange.emit($event)"
        (onNodeSelect)="onNodeSelect.emit($event)"
        (onNodeUnselect)="onNodeUnselect.emit($event)"
        (onNodeExpand)="onNodeExpand.emit($event)"
        (onNodeCollapse)="onNodeCollapse.emit($event)"
        [filter]="filter"
        [filterMode]="filterMode"
        [filterPlaceholder]="filterPlaceholder"
        [loading]="loading"
        [emptyMessage]="emptyMessage"
        styleClass="apex-p-tree">
      </p-tree>
    </div>
  `,
  styles: [`
    .apex-tree-container {
      background: var(--surface-card);
      border-radius: var(--border-radius);
      padding: 1rem;
      border: 1px solid var(--surface-border);
    }
    .apex-tree-title {
      margin-top: 0;
      margin-bottom: 1rem;
      font-size: 1.1rem;
      font-weight: 600;
      color: var(--text-color);
    }
    ::ng-deep .apex-p-tree {
      border: none;
      padding: 0;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexTreeComponent {
  @Input() value: TreeNode[] = [];
  @Input() selectionMode: 'single' | 'multiple' | 'checkbox' = 'single';
  @Input() selection: any;
  @Input() filter: boolean = false;
  @Input() filterMode: 'lenient' | 'strict' = 'lenient';
  @Input() filterPlaceholder: string = 'Search...';
  @Input() loading: boolean = false;
  @Input() emptyMessage: string = 'No records found';
  @Input() title?: string;

  @Output() onSelectionChange = new EventEmitter<any>();
  @Output() onNodeSelect = new EventEmitter<any>();
  @Output() onNodeUnselect = new EventEmitter<any>();
  @Output() onNodeExpand = new EventEmitter<any>();
  @Output() onNodeCollapse = new EventEmitter<any>();
}
