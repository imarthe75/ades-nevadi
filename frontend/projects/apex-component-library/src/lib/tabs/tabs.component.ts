import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, ContentChildren, QueryList, AfterContentInit } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'apex-tab-panel',
  template: `
    <div class="apex-tab-content" [class.active]="active" *ngIf="active">
      <ng-content></ng-content>
    </div>
  `,
  standalone: true,
  imports: [CommonModule]
})
export class ApexTabPanelComponent {
  @Input() title: string = '';
  @Input() icon?: string;
  @Input() disabled: boolean = false;
  active: boolean = false;
}

@Component({
  selector: 'apex-tabs',
  templateUrl: './tabs.component.html',
  styleUrls: ['./tabs.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule]
})
export class ApexTabsComponent implements AfterContentInit {
  @ContentChildren(ApexTabPanelComponent) tabs!: QueryList<ApexTabPanelComponent>;
  @Input() activeIndex: number = 0;
  @Input() styleClass?: string;
  @Output() activeIndexChange = new EventEmitter<number>();
  @Output() onTabChange = new EventEmitter<{ index: number, tab: ApexTabPanelComponent }>();

  ngAfterContentInit(): void {
    const activeTabs = this.tabs.filter(tab => tab.active);
    
    if (activeTabs.length === 0 && this.tabs.length > 0) {
      this.selectTab(this.activeIndex < this.tabs.length ? this.activeIndex : 0);
    }
  }

  selectTab(index: number, event?: Event): void {
    if (event) {
      event.preventDefault();
    }
    
    const tabsArray = this.tabs.toArray();
    const tabToSelect = tabsArray[index];
    
    if (!tabToSelect || tabToSelect.disabled) {
      return;
    }
    
    tabsArray.forEach(tab => tab.active = false);
    tabToSelect.active = true;
    
    this.activeIndex = index;
    this.activeIndexChange.emit(this.activeIndex);
    this.onTabChange.emit({ index, tab: tabToSelect });
  }
}
