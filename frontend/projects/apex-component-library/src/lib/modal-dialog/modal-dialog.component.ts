import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, ContentChild, TemplateRef, HostListener, ViewChild, ElementRef, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApexButtonComponent } from '../button/button.component';

@Component({
  selector: 'apex-modal-dialog',
  templateUrl: './modal-dialog.component.html',
  styleUrls: ['./modal-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, ApexButtonComponent]
})
export class ApexModalDialogComponent implements AfterViewInit {
  @Input() visible: boolean = false;
  @Input() title: string = '';
  @Input() closable: boolean = true;
  @Input() closeOnEscape: boolean = true;
  @Input() dismissableMask: boolean = true;
  @Input() width: string = '500px';
  @Input() styleClass?: string;

  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() onShow = new EventEmitter<void>();
  @Output() onHide = new EventEmitter<void>();

  @ContentChild('footer') footerTemplate?: TemplateRef<any>;
  @ViewChild('dialogElement') dialogElement!: ElementRef;

  ngAfterViewInit(): void {
    if (this.visible) {
      this.focusFirstElement();
    }
  }

  @HostListener('document:keydown.escape', ['$event'])
  onEscape(event: any): void {
    if (this.visible && this.closeOnEscape && this.closable) {
      this.close(event);
    }
  }

  onMaskClick(event: MouseEvent): void {
    if (this.dismissableMask && this.closable) {
      // Only close if clicking exactly on the mask, not inside the dialog
      if ((event.target as HTMLElement).classList.contains('apex-dialog-mask')) {
        this.close(event);
      }
    }
  }

  close(event?: Event): void {
    if (event) {
      event.preventDefault();
    }
    
    this.visible = false;
    this.visibleChange.emit(this.visible);
    this.onHide.emit();
  }

  show(): void {
    this.visible = true;
    this.visibleChange.emit(this.visible);
    this.onShow.emit();
    
    // Use timeout to let DOM update before focusing
    setTimeout(() => {
      this.focusFirstElement();
    });
  }

  private focusFirstElement(): void {
    if (!this.dialogElement) return;
    
    // Focus the dialog itself or its first focusable element
    const focusableEls = this.dialogElement.nativeElement.querySelectorAll(
      'a[href], button, textarea, input[type="text"], input[type="radio"], input[type="checkbox"], select'
    );
    
    if (focusableEls.length > 0) {
      (focusableEls[0] as HTMLElement).focus();
    } else {
      this.dialogElement.nativeElement.focus();
    }
  }
}
