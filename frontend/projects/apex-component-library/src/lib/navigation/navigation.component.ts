import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

export interface NavMenuItem {
  label: string;
  icon?: string;
  route?: string;
  url?: string;
  target?: string;
  expanded?: boolean;
  active?: boolean;
  items?: NavMenuItem[];
}

@Component({
  selector: 'apex-navigation',
  templateUrl: './navigation.component.html',
  styleUrls: ['./navigation.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, RouterModule]
})
export class ApexNavigationComponent {
  @Input() items: NavMenuItem[] = [];
  @Input() collapsed: boolean = false;
  @Input() mode: 'vertical' | 'horizontal' = 'vertical';
  
  @Output() onItemClick = new EventEmitter<{originalEvent: Event, item: NavMenuItem}>();

  toggleSubmenu(event: Event, item: NavMenuItem): void {
    if (item.items && item.items.length > 0) {
      event.preventDefault();
      item.expanded = !item.expanded;
    }
    
    this.onItemClick.emit({ originalEvent: event, item });
  }

  hasSubmenu(item: NavMenuItem): boolean {
    return !!item.items && item.items.length > 0;
  }
}
