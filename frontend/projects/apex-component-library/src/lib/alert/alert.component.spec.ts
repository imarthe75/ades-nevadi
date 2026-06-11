import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ApexAlertComponent } from './alert.component';

describe('ApexAlertComponent', () => {
  let component: ApexAlertComponent;
  let fixture: ComponentFixture<ApexAlertComponent>;
  
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ApexAlertComponent]
    }).compileComponents();
    
    fixture = TestBed.createComponent(ApexAlertComponent);
    component = fixture.componentInstance;
  });
  
  it('should create', () => {
    expect(component).toBeTruthy();
  });
  
  it('should display message', () => {
    component.message = 'Test message';
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Test message');
  });
  
  it('should emit closed event when close button clicked', (done) => {
    component.closable = true;
    component.closed.subscribe(() => {
      expect(component.visible).toBeFalsy();
      done();
    });
    
    component.onClose();
  });
  
  it('should apply correct severity class', () => {
    component.severity = 'error';
    fixture.detectChanges();
    expect(component.severityClass).toBe('alert-error');
  });
});
