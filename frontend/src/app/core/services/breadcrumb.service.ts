import { Injectable, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

/**
 * `link` is a mutable `string[]`, not `readonly string[]`, because that is what
 * Angular's `routerLink` input accepts. Declaring it readonly reads better but
 * makes the binding a compile error at every use site, which is a worse trade
 * than an array nobody mutates.
 */
export interface Crumb {
  readonly label: string;
  readonly link?: string[];
}

/**
 * Builds the breadcrumb trail from the route tree.
 *
 * Static crumbs come from each route's `data.breadcrumb`. Detail pages append
 * a dynamic crumb via {@link setDetail} once they know what they are showing —
 * a PR page cannot put "PR #482 — Fix order export" in its route config,
 * because the title only exists after the fetch resolves.
 *
 * The dynamic crumb is cleared on every navigation so a stale title can never
 * survive into an unrelated page.
 */
@Injectable({ providedIn: 'root' })
export class BreadcrumbService {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  private readonly routeCrumbs = signal<readonly Crumb[]>([]);
  private readonly detailCrumb = signal<Crumb | null>(null);

  readonly crumbs = computed<readonly Crumb[]>(() => {
    const base = this.routeCrumbs();
    const detail = this.detailCrumb();
    return detail ? [...base, detail] : base;
  });

  constructor() {
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe(() => {
        this.detailCrumb.set(null);
        this.routeCrumbs.set(this.build(this.route.root, [], []));
      });
  }

  /** Called by a detail page once it knows its subject. */
  setDetail(label: string): void {
    this.detailCrumb.set({ label });
  }

  private build(
    route: ActivatedRoute,
    urlSoFar: string[],
    acc: readonly Crumb[],
  ): readonly Crumb[] {
    const child = route.firstChild;
    if (!child) {
      return acc;
    }

    const segments = child.snapshot.url.map((segment) => segment.path);
    const url = [...urlSoFar, ...segments];
    const label = child.snapshot.data['breadcrumb'] as string | undefined;

    const next = label
      ? [...acc, { label, link: url.length ? ['/', ...url] : ['/'] }]
      : acc;

    return this.build(child, url, next);
  }
}
