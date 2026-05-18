import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnDestroy, OnInit, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { apiErrorMessage } from '../../utility/api-error.utils';
import { buildGroupedHalfHourTimeSlots, toApiTime, toHourMinuteTime, TimeSlotGroup } from '../../utility/date-time.utils';
import { createPaginationState } from '../../utility/pagination-state.utils';
import { searchTokens, trimToNull } from '../../utility/text.utils';
import { AuthService } from '../../services/auth.service';
import { AdminBranch, AdminBranchPayload, BranchAdminApiService, BranchMarket } from '../../services/branch-admin-api.service';

@Component({
  selector: 'app-admin-branches-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, TranslocoPipe],
  templateUrl: './admin-branches-page.component.html',
  styleUrl: './admin-branches-page.component.scss'
})
export class AdminBranchesPageComponent implements OnInit, OnDestroy {
  private readonly formBuilder = inject(FormBuilder);
  private readonly branchAdminApiService = inject(BranchAdminApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly authService = inject(AuthService);

  private static readonly BANNER_TIMEOUT_MS = 30_000;
  private bannerTimer: ReturnType<typeof setTimeout> | null = null;

  readonly branches = signal<AdminBranch[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly editingId = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly branchStatusUpdateIds = signal<ReadonlySet<string>>(new Set());
  readonly searchTerm = signal('');
  private readonly pagination = createPaginationState();
  readonly pageSize = this.pagination.pageSize;
  readonly startIndex = this.pagination.startIndex;
  readonly total = this.pagination.total;
  readonly hasMore = this.pagination.hasMore;
  readonly currentPage = this.pagination.currentPage;
  readonly totalPages = this.pagination.totalPages;

  readonly visibleBranches = computed(() => {
    const tokens = searchTokens(this.searchTerm());
    const currentPageBranches = this.branches();
    if (tokens.length === 0) return currentPageBranches;
    return currentPageBranches.filter((branch) => {
      const searchableBranchText = [branch.code, branch.name, branch.city, branch.province ?? '', branch.address ?? '', branch.country]
        .join(' ')
        .toLowerCase();
      return tokens.every((searchToken) => searchableBranchText.includes(searchToken));
    });
  });
  readonly loading = signal(false);
  readonly backgroundRefreshing = signal(false);
  readonly showInitialLoadingState = computed(() => this.loading() && this.branches().length === 0);
  readonly showEmptyState = computed(() => !this.loading() && this.visibleBranches().length === 0);

  readonly countries = signal<readonly string[]>([
    'South Africa',
    'Namibia',
    'Botswana',
    'Mozambique',
    'Zimbabwe',
    'Lesotho',
    'Eswatini'
  ]);

  readonly provincesByCountry = signal<Readonly<Record<string, readonly string[]>>>({});
  private readonly selectedCountry = signal<string>('South Africa');
  readonly provincesForSelectedCountry = computed<readonly string[]>(() => {
    return this.provincesByCountry()[this.selectedCountry()] ?? [];
  });

  readonly timeSlotGroups: ReadonlyArray<TimeSlotGroup> = buildGroupedHalfHourTimeSlots();

  private readonly openingTimeValue = signal<string>('09:00');

  readonly form = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(40)]],
    name: ['', [Validators.required, Validators.maxLength(160)]],
    city: ['', [Validators.required, Validators.maxLength(80)]],
    province: ['', [Validators.maxLength(80)]],
    address: ['', [Validators.maxLength(240)]],
    country: ['South Africa', [Validators.required, Validators.maxLength(80)]],
    openingTime: ['09:00', [Validators.required]],
    closingTime: ['16:00', [Validators.required]],
    adminEmail: ['', [Validators.email, Validators.maxLength(160)]]
  });

  private readonly bannerAutoDismiss = effect(() => {
    const hasMessage = this.errorMessage() !== null || this.successMessage() !== null;
    if (this.bannerTimer) {
      clearTimeout(this.bannerTimer);
      this.bannerTimer = null;
    }
    if (hasMessage) {
      this.bannerTimer = setTimeout(() => {
        this.errorMessage.set(null);
        this.successMessage.set(null);
        this.bannerTimer = null;
      }, AdminBranchesPageComponent.BANNER_TIMEOUT_MS);
    }
  });

  ngOnDestroy(): void {
    if (this.bannerTimer) {
      clearTimeout(this.bannerTimer);
      this.bannerTimer = null;
    }
  }

  async ngOnInit(): Promise<void> {
    this.form.controls.openingTime.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.openingTimeValue.set(value ?? '09:00'));
    this.selectedCountry.set(this.form.controls.country.value ?? 'South Africa');
    this.form.controls.country.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.updateSelectedCountry(value ?? ''));
    await Promise.all([this.loadCountries(), this.refresh()]);
  }

  private updateSelectedCountry(country: string): void {
    this.selectedCountry.set(country);
    const provinces = this.provincesByCountry()[country] ?? [];
    const currentProvince = this.form.controls.province.value ?? '';
    if (currentProvince && !provinces.includes(currentProvince)) {
      this.form.controls.province.setValue('', { emitEvent: false });
    }
  }

  private async loadCountries(): Promise<void> {
    try {
      const markets = await this.branchAdminApiService.listCountries();
      if (markets && typeof markets === 'object') {
        const provincesByCountry = Object.fromEntries(
          Object.entries(markets).map(([country, market]: [string, BranchMarket]) => [country, market.provinces])
        );
        this.provincesByCountry.set(provincesByCountry);
        const countryList = Object.keys(markets).sort((left, right) => left.localeCompare(right));
        if (countryList.length > 0) {
          this.countries.set(countryList);
        }
      }
    } catch {
    }
  }

  async refresh(options: { resetPage?: boolean; preserveVisibleContent?: boolean } = {}): Promise<void> {
    const { resetPage = false, preserveVisibleContent = false } = options;

    this.loading.set(true);
    this.backgroundRefreshing.set(preserveVisibleContent && this.branches().length > 0);
    this.errorMessage.set(null);
    if (resetPage) {
      this.pagination.reset();
    }
    try {
      const branchPage = await this.branchAdminApiService.list(this.startIndex(), this.pagination.endIndex());
      this.branches.set(branchPage.items);
      this.pagination.applyPage(branchPage);
    } catch (error: unknown) {
      this.errorMessage.set(apiErrorMessage(error, 'admin.loadError'));
    } finally {
      this.loading.set(false);
      this.backgroundRefreshing.set(false);
    }
  }

  isBranchStatusUpdatePending(branchId: string): boolean {
    return this.branchStatusUpdateIds().has(branchId);
  }

  private setBranchStatusUpdatePending(branchId: string, isPending: boolean): void {
    this.branchStatusUpdateIds.update((currentPendingIds) => {
      const nextPendingIds = new Set(currentPendingIds);
      if (isPending) {
        nextPendingIds.add(branchId);
      } else {
        nextPendingIds.delete(branchId);
      }
      return nextPendingIds;
    });
  }

  private updateBranchInCurrentPage(branchId: string, branchUpdater: (branch: AdminBranch) => AdminBranch): void {
    this.branches.update((currentBranches) => this.sortBranchesForAdminPage(
      currentBranches.map((branch) => (branch.id === branchId ? branchUpdater(branch) : branch))
    ));
  }

  private sortBranchesForAdminPage(branches: readonly AdminBranch[]): AdminBranch[] {
    return [...branches].sort((leftBranch, rightBranch) => {
      if (leftBranch.active !== rightBranch.active) {
        return Number(rightBranch.active) - Number(leftBranch.active);
      }
      return leftBranch.name.localeCompare(rightBranch.name);
    });
  }

  private async updateBranchActiveState(
    branch: AdminBranch,
    nextActiveState: boolean,
    successMessageKey: string,
    errorMessageKey: string
  ): Promise<void> {
    if (this.isBranchStatusUpdatePending(branch.id)) {
      return;
    }

    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.setBranchStatusUpdatePending(branch.id, true);

    try {
      if (nextActiveState) {
        await this.branchAdminApiService.reactivate(branch.id);
      } else {
        await this.branchAdminApiService.deactivate(branch.id);
      }

      this.updateBranchInCurrentPage(branch.id, (currentBranch) => ({
        ...currentBranch,
        active: nextActiveState
      }));
      this.successMessage.set(successMessageKey);
      await this.refresh({ preserveVisibleContent: true });
    } catch (error: unknown) {
      this.errorMessage.set(apiErrorMessage(error, errorMessageKey));
    } finally {
      this.setBranchStatusUpdatePending(branch.id, false);
    }
  }

  edit(branch: AdminBranch): void {
    this.editingId.set(branch.id);
    this.successMessage.set(null);
    this.errorMessage.set(null);
    this.form.reset({
      code: branch.code,
      name: branch.name,
      city: branch.city,
      province: branch.province ?? '',
      address: branch.address ?? '',
      country: branch.country,
      openingTime: toHourMinuteTime(branch.openingTime),
      closingTime: toHourMinuteTime(branch.closingTime),
      adminEmail: branch.adminEmail ?? ''
    });
  }

  cancel(): void {
    this.editingId.set(null);
    this.form.reset({
      code: '',
      name: '',
      city: '',
      province: '',
      address: '',
      country: 'South Africa',
      openingTime: '09:00',
      closingTime: '16:00',
      adminEmail: ''
    });
  }

  async submit(): Promise<void> {
    if (this.submitting()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const formValue = this.form.getRawValue();
    const payload: AdminBranchPayload = {
      code: formValue.code.trim(),
      name: formValue.name.trim(),
      city: formValue.city.trim(),
      province: trimToNull(formValue.province),
      address: trimToNull(formValue.address),
      country: formValue.country.trim(),
      openingTime: toApiTime(formValue.openingTime),
      closingTime: toApiTime(formValue.closingTime),
      adminEmail: trimToNull(formValue.adminEmail)
    };

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    try {
      const editingBranchId = this.editingId();
      if (editingBranchId) {
        const updatedBranch = await this.branchAdminApiService.update(editingBranchId, payload);
        this.updateBranchInCurrentPage(editingBranchId, () => updatedBranch);
        this.successMessage.set('admin.updated');
      } else {
        await this.branchAdminApiService.create(payload);
        this.successMessage.set('admin.created');
      }
      this.cancel();
      await this.refresh({ preserveVisibleContent: true });
    } catch (error: unknown) {
      this.errorMessage.set(apiErrorMessage(error, 'admin.saveError'));
    } finally {
      this.submitting.set(false);
    }
  }

  async deactivateBranch(branch: AdminBranch): Promise<void> {
    await this.updateBranchActiveState(branch, false, 'admin.deleted', 'admin.deleteError');
  }

  async reactivateBranch(branch: AdminBranch): Promise<void> {
    await this.updateBranchActiveState(branch, true, 'admin.reactivated', 'admin.saveError');
  }

  async signOut(): Promise<void> {
    await this.authService.logout();
    await this.router.navigateByUrl('/login');
  }

  onSearch(value: string): void {
    this.searchTerm.set(value);
  }

  onSearchInput(event: Event): void {
    const searchInput = event.target instanceof HTMLInputElement ? event.target : null;
    this.onSearch(searchInput?.value ?? '');
  }

  previousPage(): void {
    if (this.loading() || !this.pagination.previous()) return;
    void this.refresh({ preserveVisibleContent: true });
  }

  nextPage(): void {
    if (this.loading() || !this.pagination.next()) return;
    void this.refresh({ preserveVisibleContent: true });
  }

}
