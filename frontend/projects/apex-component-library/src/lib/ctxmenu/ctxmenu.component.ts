import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ContextMenuModule } from 'primeng/contextmenu';
import { ContextMenu } from 'primeng/contextmenu';
import { MenuItem } from 'primeng/api';

@Component({
  selector: 'apex-ctxmenu',
  standalone: true,
  imports: [CommonModule, ContextMenuModule],
  template: `
    <p-contextMenu 
      #cm 
      [model]="model" 
      [global]="global" 
      [target]="target" 
      [appendTo]="appendTo"
      styleClass="apex-p-contextmenu">
    </p-contextMenu>
  `,
  styles: [`
    :host {
      display: block;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexCtxMenuComponent {
  @ViewChild('cm') contextMenu!: ContextMenu;

  /** Menu items model */
  @Input() model: MenuItem[] = [];

  /** Attaches context menu to the document body instead of its parent element */
  @Input() global: boolean = false;

  /** Target element to attach the context menu to */
  @Input() target: any;

  /** Target element to append the context menu overlay to */
  @Input() appendTo: any = 'body';

  /** Shows the context menu */
  public show(event: Event): void {
    if (this.contextMenu) {
      this.contextMenu.show(event);
    }
  }

  /** Hides the context menu */
  public hide(): void {
    if (this.contextMenu) {
      this.contextMenu.hide();
    }
  }
}
