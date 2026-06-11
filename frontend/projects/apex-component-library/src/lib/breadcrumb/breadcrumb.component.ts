import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

export interface BreadcrumbItem {
  label: string;
  routerLink?: string | any[];
  icon?: string;
  activeIcon?: string;
  action?: () => void;
}

@Component({
  selector: 'apex-breadcrumb',
  templateUrl: './breadcrumb.component.html',
  styleUrls: ['./breadcrumb.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, RouterModule]
})
export class ApexBreadcrumbComponent {
  @Input() items: BreadcrumbItem[] = [];
  @Input() home?: BreadcrumbItem;
  @Input() styleClass?: string;
  @Input() separatorIcon: string = 'pi pi-chevron-right';
  
  @Output() itemClick = new EventEmitter<BreadcrumbItem>();
  
  onClick(item: BreadcrumbItem, index: number, event: Event): void {
    event.preventDefault();
    
    if (item.action) {
      item.action();
    }
    
    this.itemClick.emit(item);
  }
  
  isLast(index: number): boolean {
    return index === this.items.length - 1;
  }
}
